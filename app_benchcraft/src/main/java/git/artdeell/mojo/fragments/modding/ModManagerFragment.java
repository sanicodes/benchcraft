package git.artdeell.mojo.fragments.modding;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import git.artdeell.mojo.modding.modmanager.ContentType;
import git.artdeell.mojo.modding.modmanager.ModAdapter;
import git.artdeell.mojo.modding.modmanager.ModInfo;
import git.artdeell.mojo.modding.modmanager.ModScanner;
import git.artdeell.mojo.modding.modmanager.ModSorter;
import git.artdeell.mojo.modding.modmanager.ModTarget;
import git.artdeell.mojo.modding.modmanager.ModrinthModApi;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.FileUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;

public class ModManagerFragment extends Fragment implements ModAdapter.ModListListener {
    public static final String TAG = "ModManagerFragment";
    public static final String ARG_CONTENT_TYPE = "content_type";
    private static final String PREF_SORT = "modManagerSort";
    private RecyclerView mModRecyclerView;
    private Spinner mSortSpinner;
    private boolean mModrinthStatsLoaded;
    private ModAdapter mStatsLoadingAdapter;
    private ProgressBar mProgressBar;
    private EditText mSearchEditText;
    private TextView mSummaryTextView;
    private TextView mEmptyTextView;
    private View mHintLayout;
    private TextView mHintTextView;
    private Button mHintButton;
    private final EnumMap<ContentType, TextView> mTabs = new EnumMap<>(ContentType.class);
    private Instance mInstance;
    private ModTarget mModTarget;
    private ModAdapter mModAdapter;
    private ContentType mContentType;
    private ContentType mImportType;
    private int mScanGeneration;

    private final ActivityResultLauncher<Object> mJarImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), this::onImportPicked);
    private final ActivityResultLauncher<Object> mZipImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("zip"), this::onImportPicked);

    public ModManagerFragment() {
        super(R.layout.fragment_mod_manager);
    }

    private void onImportPicked(Uri data) {
        if(data == null || mModTarget == null || mImportType == null) return;
        Context context = getContext();
        if(context == null) return;
        String fileName = Tools.getFileName(context, data);
        if(fileName == null) return;
        File directory = mModTarget.getDirectory(mImportType);
        MojoApplication.sExecutorService.execute(()->importFile(fileName, data, context, directory));
    }

    private void importFile(String fileName, Uri uri, Context context, File directory) {
        FileUtils.ensureDirectorySilently(directory);
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(new File(directory, fileName))){
                if(inputStream == null) throw new IOException("Failed to open input stream");
                IOUtils.copy(inputStream, outputStream);
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
        Tools.runOnUiThread(()->{
            if(getView() != null) refreshContent();
        });
    }

    private void refreshContent() {
        mProgressBar.setVisibility(View.VISIBLE);
        Instance instance = mInstance;
        ContentType requestedType = mContentType;
        int generation = ++mScanGeneration;
        MojoApplication.sExecutorService.execute(()->{
            ModTarget modTarget = ModTarget.fromInstance(instance);
            ContentType type = requestedType != null && modTarget.supports(requestedType) ? requestedType : getFirstSupportedType(modTarget);
            File directory = modTarget.getDirectory(type);
            List<ModInfo> contentList = type == ContentType.MODS
                    ? ModScanner.findMods(directory, modTarget.platform)
                    : ModScanner.findPacks(directory, type);
            if(contentList == null) contentList = new ArrayList<>();
            Collections.sort(contentList, ModSorter.comparator(ModSorter.SORT_NAME));
            boolean hasShaderLoader = type == ContentType.SHADERS && modTarget.hasShaderLoader();
            List<ModInfo> finalContentList = contentList;
            Tools.runOnUiThread(()->{
                if(getView() == null || generation != mScanGeneration) return;
                mProgressBar.setVisibility(View.GONE);
                mModTarget = modTarget;
                mContentType = type;
                updateTabs();
                updateHint(hasShaderLoader);
                mModAdapter = new ModAdapter(finalContentList, modTarget.platform, this);
                mModrinthStatsLoaded = false;
                mModRecyclerView.setAdapter(mModAdapter);
                applySearch();
                applySort();
            });
        });
    }

    private static ContentType getFirstSupportedType(ModTarget modTarget) {
        for(ContentType type : ContentType.values()) {
            if(modTarget.supports(type)) return type;
        }
        return ContentType.RESOURCE_PACKS;
    }

    private void selectTab(ContentType type) {
        if(type == mContentType) return;
        mContentType = type;
        mModAdapter = null;
        mModRecyclerView.setAdapter(null);
        mEmptyTextView.setVisibility(View.GONE);
        mSearchEditText.setText("");
        updateTabs();
        mHintLayout.setVisibility(View.GONE);
        refreshContent();
    }

    private void updateTabs() {
        int supportedCount = 0;
        for(ContentType type : ContentType.values()) {
            TextView tab = mTabs.get(type);
            boolean supported = mModTarget != null && mModTarget.supports(type);
            if(supported) supportedCount++;
            tab.setVisibility(supported ? View.VISIBLE : View.GONE);
            tab.setSelected(type == mContentType);
        }
        ((View) mTabs.get(ContentType.MODS).getParent()).setVisibility(supportedCount > 1 ? View.VISIBLE : View.GONE);
        mSearchEditText.setHint(getSearchHint(mContentType));
    }

    private void updateHint(boolean hasShaderLoader) {
        mHintButton.setVisibility(View.GONE);
        if(mContentType == ContentType.RESOURCE_PACKS) {
            mHintTextView.setText(R.string.mod_manager_hint_resource_packs);
        }else if(mContentType == ContentType.SHADERS && hasShaderLoader) {
            mHintTextView.setText(R.string.mod_manager_hint_shaders);
        }else if(mContentType == ContentType.SHADERS) {
            String shaderMod = mModTarget.getShaderModName();
            mHintTextView.setText(getString(R.string.mod_manager_hint_shaders_no_loader, shaderMod));
            mHintButton.setText(getString(R.string.mod_manager_get_shader_mod, shaderMod));
            mHintButton.setVisibility(View.VISIBLE);
            mHintButton.setOnClickListener(v -> openBrowser(ContentType.MODS, shaderMod));
        }else {
            mHintLayout.setVisibility(View.GONE);
            return;
        }
        mHintLayout.setVisibility(View.VISIBLE);
    }

    private void openBrowser(ContentType type, @Nullable String query) {
        Bundle args = new Bundle();
        args.putString(ModBrowserFragment.ARG_CONTENT_TYPE, type.name());
        if(query != null) args.putString(ModBrowserFragment.ARG_QUERY, query);
        Tools.swapFragment(requireActivity(), ModBrowserFragment.class, ModBrowserFragment.TAG, args);
    }

    private void importContent() {
        if(mContentType == null) return;
        mImportType = mContentType;
        if(mContentType == ContentType.MODS) mJarImportLauncher.launch(null);
        else mZipImportLauncher.launch(null);
    }

    private static int getSearchHint(@Nullable ContentType type) {
        if(type == ContentType.RESOURCE_PACKS) return R.string.mod_manager_search_hint_resource_packs;
        if(type == ContentType.SHADERS) return R.string.mod_manager_search_hint_shaders;
        return R.string.mod_manager_search_hint;
    }

    private static int getCountPlurals(ContentType type) {
        if(type == ContentType.RESOURCE_PACKS) return R.plurals.mod_manager_resource_pack_count;
        if(type == ContentType.SHADERS) return R.plurals.mod_manager_shader_count;
        return R.plurals.mod_manager_mod_count;
    }

    private static int getEmptyText(ContentType type) {
        if(type == ContentType.RESOURCE_PACKS) return R.string.mod_manager_empty_resource_packs;
        if(type == ContentType.SHADERS) return R.string.mod_manager_empty_shaders;
        return R.string.mod_manager_empty;
    }

    private void applySort() {
        if(mModAdapter == null) return;
        int sort = mSortSpinner.getSelectedItemPosition();
        if(ModSorter.needsModrinthStats(sort) && !mModrinthStatsLoaded) {
            loadModrinthStats();
            return;
        }
        mModAdapter.sort(ModSorter.comparator(sort));
    }

    private void loadModrinthStats() {
        if(mStatsLoadingAdapter == mModAdapter) return;
        ModAdapter adapter = mModAdapter;
        mStatsLoadingAdapter = adapter;
        mProgressBar.setVisibility(View.VISIBLE);
        List<ModInfo> mods = adapter.getMods();
        MojoApplication.sExecutorService.execute(()->{
            boolean loaded = ModSorter.loadModrinthStats(new ModrinthModApi(), mods);
            Tools.runOnUiThread(()->{
                if(mStatsLoadingAdapter == adapter) mStatsLoadingAdapter = null;
                if(getView() == null || adapter != mModAdapter) return;
                mProgressBar.setVisibility(View.GONE);
                if(!loaded) Toast.makeText(requireContext(), R.string.mod_browser_error, Toast.LENGTH_SHORT).show();
                mModrinthStatsLoaded = loaded;
                adapter.sort(ModSorter.comparator(mSortSpinner.getSelectedItemPosition()));
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
        String countText = getResources().getQuantityString(getCountPlurals(mContentType), modCount, modCount);
        if(disabledCount > 0) countText = getString(R.string.mod_manager_disabled_count, countText, disabledCount);
        mSummaryTextView.setText(getString(R.string.mod_manager_summary, mModTarget.getLoaderName(), mcVersion, countText));

        boolean isEmpty = mModAdapter.getDisplayedModCount() == 0;
        mEmptyTextView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mEmptyTextView.setText(modCount == 0 ? getEmptyText(mContentType) : R.string.mod_manager_no_matches);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstance = Instances.loadSelectedInstance();
        if(mInstance == null) {
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        if(mContentType == null && getArguments() != null) {
            String typeName = getArguments().getString(ARG_CONTENT_TYPE);
            if(typeName != null) mContentType = ContentType.valueOf(typeName);
        }
        mModRecyclerView = view.findViewById(R.id.mod_manager_recyclerview);
        mProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mSummaryTextView = view.findViewById(R.id.mod_manager_summary_textview);
        mEmptyTextView = view.findViewById(R.id.mod_manager_empty_textview);
        TextView titleTextView = view.findViewById(R.id.mod_manager_title_textview);
        View mOverlay = view.findViewById(R.id.search_mod_overlay);
        Button mImportButton = view.findViewById(R.id.mod_manager_import_button);
        Button mBrowseButton = view.findViewById(R.id.mod_manager_browse_button);
        mHintLayout = view.findViewById(R.id.mod_manager_hint_layout);
        mHintTextView = view.findViewById(R.id.mod_manager_hint_textview);
        mHintButton = view.findViewById(R.id.mod_manager_hint_button);
        mTabs.put(ContentType.MODS, view.findViewById(R.id.mod_manager_tab_mods));
        mTabs.put(ContentType.RESOURCE_PACKS, view.findViewById(R.id.mod_manager_tab_resource_packs));
        mTabs.put(ContentType.SHADERS, view.findViewById(R.id.mod_manager_tab_shaders));
        for(ContentType type : ContentType.values()) {
            mTabs.get(type).setOnClickListener(v -> selectTab(type));
        }

        titleTextView.setText(mInstance.name);
        mImportButton.setOnClickListener(v-> importContent());
        mBrowseButton.setOnClickListener(v-> {
            if(mContentType != null) openBrowser(mContentType, null);
        });
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
        mSortSpinner = view.findViewById(R.id.mod_manager_sort_spinner);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(view.getContext(),
                R.array.mod_manager_sort_names, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSortSpinner.setAdapter(sortAdapter);
        int savedSort = LauncherPreferences.DEFAULT_PREF.getInt(PREF_SORT, ModSorter.SORT_NAME);
        if(savedSort >= 0 && savedSort < sortAdapter.getCount()) mSortSpinner.setSelection(savedSort);
        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View itemView, int position, long id) {
                LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_SORT, position).apply();
                applySort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        mModRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        Tools.setupResizableOverlayView(mModRecyclerView, mOverlay);
        mModAdapter = null;
        updateTabs();
        refreshContent();
    }
}
