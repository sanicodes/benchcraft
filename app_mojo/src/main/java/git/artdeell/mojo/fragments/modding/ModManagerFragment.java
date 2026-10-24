package git.artdeell.mojo.fragments.modding;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modmanager.ModAdapter;
import git.artdeell.mojo.modding.modmanager.ModInfo;
import git.artdeell.mojo.modding.modmanager.ModScanner;
import git.artdeell.mojo.modding.modmanager.ModTarget;
import git.artdeell.mojo.utils.FileUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;

public class ModManagerFragment extends Fragment implements ModAdapter.ModListListener {
    public static final String TAG = "ModManagerFragment";
    private RecyclerView mModRecyclerView;
    private ProgressBar mProgressBar;
    private EditText mSearchEditText;
    private TextView mSummaryTextView;
    private TextView mEmptyTextView;
    private Instance mInstance;
    private ModTarget mModTarget;
    private ModAdapter mModAdapter;

    private final ActivityResultLauncher<Object> mModImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data == null || mModTarget == null) return;
                Context context = getContext();
                if(context == null) return;
                String fileName = Tools.getFileName(context, data);
                if(fileName == null) return;
                File modsDirectory = mModTarget.modsDirectory;
                MojoApplication.sExecutorService.execute(()->importMod(fileName, data, context, modsDirectory));
            });

    public ModManagerFragment() {
        super(R.layout.fragment_mod_manager);
    }

    private void importMod(String fileName, Uri modUri, Context context, File modsDirectory) {
        FileUtils.ensureDirectorySilently(modsDirectory);
        try (InputStream inputStream = context.getContentResolver().openInputStream(modUri);
             OutputStream outputStream = new FileOutputStream(new File(modsDirectory, fileName))){
                if(inputStream == null) throw new IOException("Failed to open mod input stream");
                IOUtils.copy(inputStream, outputStream);
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
        Tools.runOnUiThread(()->{
            if(getView() != null) refreshModsList();
        });
    }

    private void refreshModsList() {
        mProgressBar.setVisibility(View.VISIBLE);
        Instance instance = mInstance;
        MojoApplication.sExecutorService.execute(()->{
            ModTarget modTarget = ModTarget.fromInstance(instance);
            List<ModInfo> modInfoList = null;
            if(modTarget != null) modInfoList = ModScanner.findMods(modTarget.modsDirectory, modTarget.platform);
            if(modInfoList == null) modInfoList = new ArrayList<>();
            Collections.sort(modInfoList, (a, b) -> a.getDisplayFileName().compareToIgnoreCase(b.getDisplayFileName()));
            List<ModInfo> finalModInfoList = modInfoList;
            Tools.runOnUiThread(()->{
                if(getView() == null) return;
                mProgressBar.setVisibility(View.GONE);
                mModTarget = modTarget;
                if(modTarget == null) {
                    mSummaryTextView.setText(R.string.mod_manager_no_loader);
                    return;
                }
                mModAdapter = new ModAdapter(finalModInfoList, modTarget.platform, this);
                mModRecyclerView.setAdapter(mModAdapter);
                applySearch();
            });
        });
    }

    private void applySearch() {
        if(mModAdapter == null) return;
        String searchText = mSearchEditText.getText().toString().toLowerCase(Locale.ROOT).trim();
        if(searchText.isEmpty()) mModAdapter.performSearch(m -> true);
        else mModAdapter.performSearch(m -> m.searchTerms.contains(searchText));
    }

    @Override
    public void onModListChanged() {
        if(mModAdapter == null || mModTarget == null) return;
        String mcVersion = mModTarget.mcVersion != null ? mModTarget.mcVersion : getString(R.string.mod_manager_unknown_mc_version);
        int modCount = mModAdapter.getModCount();
        int disabledCount = mModAdapter.getDisabledModCount();
        String countText = getResources().getQuantityString(R.plurals.mod_manager_mod_count, modCount, modCount);
        if(disabledCount > 0) countText = getString(R.string.mod_manager_disabled_count, countText, disabledCount);
        mSummaryTextView.setText(getString(R.string.mod_manager_summary, mModTarget.getLoaderName(), mcVersion, countText));

        boolean isEmpty = mModAdapter.getDisplayedModCount() == 0;
        mEmptyTextView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mEmptyTextView.setText(modCount == 0 ? R.string.mod_manager_empty : R.string.mod_browser_no_results);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstance = Instances.loadSelectedInstance();
        if(mInstance == null) {
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        mModRecyclerView = view.findViewById(R.id.mod_manager_recyclerview);
        mProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mSummaryTextView = view.findViewById(R.id.mod_manager_summary_textview);
        mEmptyTextView = view.findViewById(R.id.mod_manager_empty_textview);
        TextView titleTextView = view.findViewById(R.id.mod_manager_title_textview);
        View mOverlay = view.findViewById(R.id.search_mod_overlay);
        Button mImportButton = view.findViewById(R.id.mod_manager_import_button);
        Button mBrowseButton = view.findViewById(R.id.mod_manager_browse_button);

        titleTextView.setText(mInstance.name);
        mImportButton.setOnClickListener(v-> mModImportLauncher.launch(null));
        mBrowseButton.setOnClickListener(v->
                Tools.swapFragment(requireActivity(), ModBrowserFragment.class, ModBrowserFragment.TAG, null));
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applySearch();
            }
        });
        mModRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        Tools.setupOverlayView(mModRecyclerView, mOverlay);
        refreshModsList();
    }
}
