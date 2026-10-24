package git.artdeell.mojo.fragments.instance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.modding.modmanager.ModrinthModApi;
import git.artdeell.mojo.modding.modmanager.ModrinthProject;
import git.artdeell.mojo.modding.modpacks.imagecache.ImageReceiver;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;

/**
 * Searches Modrinth and hands back the icon of the project the user taps, to use as an instance icon.
 */
public class ModrinthIconPickerDialog {
    private static final int RESULT_LIMIT = 48;
    private static final int GRID_COLUMNS = 4;
    // Shared by every picker, since the cache's thread pool is never shut down
    private static ModIconCache sIconCache;

    public interface Callback {
        void onIconPicked(@NonNull Bitmap icon);
    }

    private final ModrinthModApi mApi = new ModrinthModApi();
    private final IconAdapter mAdapter = new IconAdapter();
    private final Callback mCallback;
    private AlertDialog mDialog;
    private ProgressBar mProgressBar;
    private TextView mStatusTextView;
    private int mSearchGeneration;

    private ModrinthIconPickerDialog(Callback callback) {
        mCallback = callback;
    }

    public static void show(@NonNull Context context, @NonNull Callback callback) {
        new ModrinthIconPickerDialog(callback).create(context);
    }

    private static ModIconCache getIconCache() {
        if(sIconCache == null) sIconCache = new ModIconCache();
        return sIconCache;
    }

    @SuppressLint("InflateParams") // Dialog views have no parent to inflate into
    private void create(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_modrinth_icon_picker, null);
        EditText searchEditText = view.findViewById(R.id.icon_picker_search);
        mProgressBar = view.findViewById(R.id.icon_picker_progress);
        mStatusTextView = view.findViewById(R.id.icon_picker_status);
        RecyclerView grid = view.findViewById(R.id.icon_picker_grid);
        grid.setLayoutManager(new GridLayoutManager(context, GRID_COLUMNS));
        grid.setAdapter(mAdapter);

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            search(v.getText().toString().trim());
            hideKeyboard(v);
            return true;
        });

        mDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.instance_icon_modrinth_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mDialog.setOnDismissListener(d -> {
            // Ignore searches and icons that finish after the dialog is gone
            mSearchGeneration++;
            mAdapter.cancelImages();
        });
        mDialog.show();
        search("");
    }

    private void search(String query) {
        int generation = ++mSearchGeneration;
        mAdapter.setProjects(new ArrayList<>());
        mStatusTextView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        MojoApplication.sExecutorService.execute(() -> {
            ModrinthModApi.SearchPage page = mApi.searchIcons(query, RESULT_LIMIT);
            Tools.runOnUiThread(() -> {
                if(generation != mSearchGeneration) return;
                mProgressBar.setVisibility(View.GONE);
                if(page == null || page.hits == null) {
                    showStatus(R.string.mod_browser_error);
                    return;
                }
                List<ModrinthProject> projects = new ArrayList<>();
                for(ModrinthProject project : page.hits) {
                    // Projects without an icon have nothing to offer here
                    if(project.icon_url != null && !project.icon_url.isEmpty()) projects.add(project);
                }
                mAdapter.setProjects(projects);
                if(projects.isEmpty()) showStatus(R.string.mod_browser_no_results);
            });
        });
    }

    private void showStatus(int message) {
        mStatusTextView.setText(message);
        mStatusTextView.setVisibility(View.VISIBLE);
    }

    private static void hideKeyboard(View view) {
        view.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(inputMethodManager != null) inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private class IconAdapter extends RecyclerView.Adapter<IconViewHolder> {
        private final List<ModrinthProject> mProjects = new ArrayList<>();
        private final List<IconViewHolder> mHolders = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void setProjects(List<ModrinthProject> projects) {
            mProjects.clear();
            mProjects.addAll(projects);
            notifyDataSetChanged();
        }

        void cancelImages() {
            for(IconViewHolder holder : mHolders) holder.cancelImage();
        }

        @NonNull
        @Override
        public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_modrinth_icon, parent, false);
            IconViewHolder holder = new IconViewHolder(view);
            mHolders.add(holder);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
            holder.bind(mProjects.get(position));
        }

        @Override
        public int getItemCount() {
            return mProjects.size();
        }
    }

    private class IconViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mImage;
        private final TextView mTitle;
        private ImageReceiver mImageReceiver;
        private Bitmap mIcon;

        IconViewHolder(View view) {
            super(view);
            mImage = view.findViewById(R.id.modrinth_icon_image);
            mTitle = view.findViewById(R.id.modrinth_icon_title);
            view.setOnClickListener(v -> {
                // The loaded image is what gets used, so there is nothing to pick before it arrives
                if(mIcon == null) return;
                mCallback.onIconPicked(mIcon);
                mDialog.dismiss();
            });
        }

        void bind(ModrinthProject project) {
            cancelImage();
            mIcon = null;
            mImage.setImageDrawable(null);
            mTitle.setText(project.title);
            itemView.setContentDescription(project.title);
            mImageReceiver = bitmap -> {
                mImageReceiver = null;
                if(bitmap == null) return;
                mIcon = bitmap;
                mImage.setImageDrawable(InstanceIconProvider.createCustomIcon(mImage.getResources(), bitmap));
            };
            getIconCache().getImage(mImageReceiver, project.getIconCacheTag(), project.icon_url);
        }

        void cancelImage() {
            if(mImageReceiver == null) return;
            getIconCache().cancelImage(mImageReceiver);
            mImageReceiver = null;
        }
    }
}
