package git.artdeell.mojo.modding.modmanager;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import git.artdeell.mojo.R;
import git.artdeell.mojo.modding.modpacks.imagecache.ImageReceiver;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;

public class ModBrowserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_MOD = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final ModIconCache mIconCache = new ModIconCache();
    private final ArrayList<ModrinthProject> mProjects = new ArrayList<>();
    private final Callback mCallback;
    private InstalledModIndex mInstalledModIndex = InstalledModIndex.empty();
    private boolean mHasMore;

    public ModBrowserAdapter(Callback callback) {
        mCallback = callback;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setProjects(ModrinthProject[] projects, boolean hasMore) {
        mProjects.clear();
        mProjects.addAll(Arrays.asList(projects));
        mHasMore = hasMore;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addProjects(ModrinthProject[] projects, boolean hasMore) {
        mProjects.addAll(Arrays.asList(projects));
        mHasMore = hasMore;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clear() {
        mProjects.clear();
        mHasMore = false;
        notifyDataSetChanged();
    }

    public int getProjectCount() {
        return mProjects.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setInstalledModIndex(InstalledModIndex installedModIndex) {
        mInstalledModIndex = installedModIndex;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < mProjects.size() ? VIEW_TYPE_MOD : VIEW_TYPE_LOADING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if(viewType == VIEW_TYPE_LOADING) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.view_loading, parent, false)) {};
        }
        return new ViewHolder(inflater.inflate(R.layout.view_mod_browser_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof ViewHolder) {
            ((ViewHolder) holder).bind(mProjects.get(position));
        } else {
            mCallback.onLoadMore();
        }
    }

    @Override
    public int getItemCount() {
        return mProjects.size() + (mHasMore ? 1 : 0);
    }

    static String formatCount(long count) {
        if(count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000d);
        if(count >= 1_000) return String.format(Locale.ROOT, "%.1fK", count / 1_000d);
        return Long.toString(count);
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mTitle, mMeta, mDescription, mInstalledBadge;
        private ModrinthProject mProject;
        private ImageReceiver mImageReceiver;

        ViewHolder(View view) {
            super(view);
            mIcon = view.findViewById(R.id.mod_thumbnail_imageview);
            mTitle = view.findViewById(R.id.mod_title_textview);
            mMeta = view.findViewById(R.id.mod_browser_meta_textview);
            mDescription = view.findViewById(R.id.mod_body_textview);
            mInstalledBadge = view.findViewById(R.id.mod_browser_installed_badge);
            view.setOnClickListener(v -> {
                if(mProject != null) mCallback.onModClicked(mProject);
            });
        }

        void bind(ModrinthProject project) {
            mProject = project;
            mTitle.setText(project.title);
            mDescription.setText(project.description);
            mMeta.setText(mMeta.getContext().getString(R.string.mod_browser_meta, project.author, formatCount(project.downloads)));
            mInstalledBadge.setVisibility(mInstalledModIndex.isInstalled(project.project_id) ? View.VISIBLE : View.GONE);

            if(mImageReceiver != null) mIconCache.cancelImage(mImageReceiver);
            mIcon.setImageDrawable(null);
            if(project.icon_url == null || project.icon_url.isEmpty()) {
                mImageReceiver = null;
                return;
            }
            mImageReceiver = bitmap -> {
                mImageReceiver = null;
                if(bitmap == null) return;
                RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(mIcon.getResources(), bitmap);
                mIcon.setImageDrawable(drawable);
            };
            mIconCache.getImage(mImageReceiver, project.getIconCacheTag(), project.icon_url);
        }
    }

    public interface Callback {
        void onModClicked(ModrinthProject project);
        void onLoadMore();
    }
}
