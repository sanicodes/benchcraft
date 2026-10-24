package git.artdeell.mojo.fragments.modding;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modmanager.InstalledModIndex;
import git.artdeell.mojo.modding.modmanager.ModBrowserAdapter;
import git.artdeell.mojo.modding.modmanager.ModTarget;
import git.artdeell.mojo.modding.modmanager.ModrinthModApi;
import git.artdeell.mojo.modding.modmanager.ModrinthModInstaller;
import git.artdeell.mojo.modding.modmanager.ModrinthProject;
import git.artdeell.mojo.modding.modmanager.ModrinthVersion;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;

public class ModBrowserFragment extends Fragment implements ModBrowserAdapter.Callback {
    public static final String TAG = "ModBrowserFragment";
    private static final String[] SORT_INDICES = {
            ModrinthModApi.SORT_RELEVANCE,
            ModrinthModApi.SORT_DOWNLOADS,
            ModrinthModApi.SORT_FOLLOWS,
            ModrinthModApi.SORT_NEWEST,
            ModrinthModApi.SORT_UPDATED
    };

    private final ModrinthModApi mApi = new ModrinthModApi();
    private ModBrowserAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private EditText mSearchEditText;
    private ProgressBar mProgressBar;
    private TextView mFilterTextView;
    private TextView mStatusTextView;
    private ModTarget mModTarget;
    private Future<InstalledModIndex> mInstalledModIndex;
    private String mQuery = "";
    private String mSortIndex = ModrinthModApi.SORT_RELEVANCE;
    private int mSearchGeneration;
    private int mNextOffset;
    private boolean mLoadingPage;
    private int mRunningRequests;

    public ModBrowserFragment() {
        super(R.layout.fragment_mod_browser);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        mRecyclerView = view.findViewById(R.id.search_mod_list);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mFilterTextView = view.findViewById(R.id.mod_browser_filter_textview);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        Spinner sortSpinner = view.findViewById(R.id.mod_browser_sort_spinner);

        mAdapter = new ModBrowserAdapter(this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mRecyclerView.setAdapter(mAdapter);
        Tools.setupOverlayView(mRecyclerView, view.findViewById(R.id.search_mod_overlay));

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(view.getContext(),
                R.array.mod_browser_sort_names, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String sortIndex = SORT_INDICES[position];
                if(sortIndex.equals(mSortIndex)) return;
                mSortIndex = sortIndex;
                startSearch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            mQuery = mSearchEditText.getText().toString().trim();
            startSearch();
            hideKeyboard();
            return true;
        });

        setLoading(true);
        MojoApplication.sExecutorService.execute(() -> {
            ModTarget modTarget = ModTarget.fromInstance(instance);
            Tools.runOnUiThread(() -> {
                if(getView() == null) return;
                setLoading(false);
                if(modTarget == null) {
                    showStatus(R.string.mod_manager_no_loader);
                    return;
                }
                onTargetReady(modTarget);
            });
        });
    }

    private void onTargetReady(ModTarget modTarget) {
        mModTarget = modTarget;
        if(modTarget.mcVersion != null) {
            mFilterTextView.setText(getString(R.string.mod_browser_filter, modTarget.getLoaderName(), modTarget.mcVersion));
        } else {
            mFilterTextView.setText(getString(R.string.mod_browser_filter_any_version, modTarget.getLoaderName()));
        }
        startSearch();
        mInstalledModIndex = MojoApplication.sExecutorService.submit(() -> {
            InstalledModIndex index = InstalledModIndex.build(mApi, modTarget.modsDirectory);
            Tools.runOnUiThread(() -> {
                if(mAdapter != null) mAdapter.setInstalledModIndex(index);
            });
            return index;
        });
    }

    private void startSearch() {
        if(mModTarget == null) return;
        mSearchGeneration++;
        mNextOffset = 0;
        mStatusTextView.setVisibility(View.GONE);
        mAdapter.clear();
        loadPage(mSearchGeneration, 0);
    }

    private void loadPage(int generation, int offset) {
        mLoadingPage = true;
        setLoading(true);
        ModTarget modTarget = mModTarget;
        String query = mQuery;
        String sortIndex = mSortIndex;
        MojoApplication.sExecutorService.execute(() -> {
            ModrinthModApi.SearchPage page = mApi.search(query, modTarget, sortIndex, offset);
            Tools.runOnUiThread(() -> {
                if(getView() == null) return;
                setLoading(false);
                if(generation != mSearchGeneration) return;
                mLoadingPage = false;
                if(page == null || page.hits == null) {
                    if(offset == 0) showStatus(R.string.mod_browser_error);
                    else mAdapter.addProjects(new ModrinthProject[0], false);
                    return;
                }
                mNextOffset = offset + page.hits.length;
                boolean hasMore = page.hits.length > 0 && mNextOffset < page.total_hits;
                if(offset == 0) {
                    mAdapter.setProjects(page.hits, hasMore);
                    if(page.hits.length == 0) showStatus(R.string.mod_browser_no_results);
                    mRecyclerView.scrollToPosition(0);
                } else {
                    mAdapter.addProjects(page.hits, hasMore);
                }
            });
        });
    }

    @Override
    public void onLoadMore() {
        if(mLoadingPage || mModTarget == null) return;
        int generation = mSearchGeneration;
        mRecyclerView.post(() -> {
            if(mLoadingPage || generation != mSearchGeneration) return;
            loadPage(generation, mNextOffset);
        });
    }

    @Override
    public void onModClicked(ModrinthProject project) {
        if(mModTarget == null) return;
        ModTarget modTarget = mModTarget;
        Future<InstalledModIndex> indexFuture = mInstalledModIndex;
        setLoading(true);
        MojoApplication.sExecutorService.execute(() -> {
            ModrinthVersion[] versions = mApi.getVersions(project.project_id, modTarget);
            InstalledModIndex index = waitForIndex(indexFuture);
            Tools.runOnUiThread(() -> {
                if(getView() == null) return;
                setLoading(false);
                Context context = requireContext();
                if(versions == null) {
                    Toast.makeText(context, R.string.mod_browser_error, Toast.LENGTH_SHORT).show();
                } else if(versions.length == 0) {
                    String mcVersion = modTarget.mcVersion != null ? modTarget.mcVersion : getString(R.string.mod_manager_unknown_mc_version);
                    Toast.makeText(context, getString(R.string.mod_browser_no_versions, modTarget.getLoaderName(), mcVersion), Toast.LENGTH_LONG).show();
                } else {
                    showVersionDialog(context, project, versions, index);
                }
            });
        });
    }

    private void showVersionDialog(Context context, ModrinthProject project, ModrinthVersion[] versions, InstalledModIndex index) {
        String installedVersionId = index.getVersionId(project.project_id);
        ModrinthVersion suggested = ModrinthModInstaller.pickVersion(versions);
        String[] labels = new String[versions.length];
        int[] selected = {0};
        for(int i = 0; i < versions.length; i++) {
            ModrinthVersion version = versions[i];
            String label = version.version_number;
            if(!version.isRelease() && version.version_type != null) label += " · " + version.version_type;
            if(version.id.equals(installedVersionId)) label = getString(R.string.mod_browser_version_installed, label);
            labels[i] = label;
            if(version == suggested) selected[0] = i;
        }
        new AlertDialog.Builder(context)
                .setTitle(project.title)
                .setSingleChoiceItems(labels, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.mod_browser_install, (d, w) -> installVersion(project, versions[selected[0]], index))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void installVersion(ModrinthProject project, ModrinthVersion version, InstalledModIndex index) {
        Context appContext = requireContext().getApplicationContext();
        if(ProgressKeeper.hasProgressKey(ProgressLayout.INSTALL_MODPACK)) {
            Toast.makeText(appContext, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.mod_browser_resolving_dependencies);
        ModTarget modTarget = mModTarget;
        MojoApplication.sExecutorService.execute(() -> {
            try {
                ModrinthModInstaller.Result result = ModrinthModInstaller.install(mApi, modTarget, version, index);
                Tools.runOnUiThread(() -> {
                    Toast.makeText(appContext, getResultMessage(appContext, project, result), Toast.LENGTH_LONG).show();
                    if(mAdapter != null) mAdapter.notifyDataSetChanged();
                });
            }catch (IOException | InterruptedException e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(appContext, R.string.mod_browser_install_failed, e);
            }
        });
    }

    private static String getResultMessage(Context context, ModrinthProject project, ModrinthModInstaller.Result result) {
        if(!result.missingDependencies.isEmpty()) {
            return context.getString(R.string.mod_browser_missing_dependencies, project.title,
                    TextUtils.join(", ", result.missingDependencies));
        }
        if(result.installedDependencies > 0) {
            return context.getResources().getQuantityString(R.plurals.mod_browser_install_done_with_dependencies,
                    result.installedDependencies, project.title, result.installedDependencies);
        }
        return context.getString(R.string.mod_browser_install_done, project.title);
    }

    private static InstalledModIndex waitForIndex(@Nullable Future<InstalledModIndex> indexFuture) {
        if(indexFuture == null) return InstalledModIndex.empty();
        try {
            return indexFuture.get();
        }catch (ExecutionException | InterruptedException e) {
            Log.w(TAG, "Failed to index installed mods", e);
            return InstalledModIndex.empty();
        }
    }

    private void setLoading(boolean loading) {
        mRunningRequests += loading ? 1 : -1;
        mProgressBar.setVisibility(mRunningRequests > 0 ? View.VISIBLE : View.GONE);
    }

    private void showStatus(int message) {
        mStatusTextView.setText(message);
        mStatusTextView.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        mSearchEditText.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(inputMethodManager != null) inputMethodManager.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
    }
}
