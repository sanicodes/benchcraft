package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.fragments.misc.GamepadMapperFragment;
import git.artdeell.mojo.fragments.modding.ModManagerFragment;
import git.artdeell.mojo.game.renderer.GameRenderer;
import git.artdeell.mojo.game.renderer.RenderSpec;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modmanager.ModInfo;
import git.artdeell.mojo.modding.modmanager.ModTarget;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

import me.andreasmelone.basicmodinfoparser.platform.Platform;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private static final float UNAVAILABLE_TILE_ALPHA = 0.5f;

    private mcVersionSpinner mVersionSpinner;
    private TextView mInstanceDetails, mInstanceRenderer;
    private View mModsTile, mControlsTile;
    private int mSummaryGeneration;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mInstanceDetails = view.findViewById(R.id.dashboard_instance_details);
        mInstanceRenderer = view.findViewById(R.id.dashboard_instance_renderer);
        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);

        mModsTile = setupTile(view, R.id.dashboard_mods_tile, R.drawable.ic_px_mods, R.string.dashboard_mods,
                v -> openModManager(v.getContext()));
        mControlsTile = setupTile(view, R.id.dashboard_controls_tile, R.drawable.ic_px_gamepad, R.string.dashboard_controls,
                v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        View folderTile = setupTile(view, R.id.dashboard_folder_tile, R.drawable.ic_px_folder, R.string.dashboard_game_folder,
                v -> openGameDirectory(v.getContext()));
        View jarTile = setupTile(view, R.id.dashboard_jar_tile, R.drawable.ic_px_java_run, R.string.dashboard_run_jar,
                v -> runInstallerWithConfirmation());
        View logsTile = setupTile(view, R.id.dashboard_logs_tile, R.drawable.ic_px_sharelog, R.string.dashboard_share_logs,
                v -> shareLog(requireContext()));
        setTileSubtitle(folderTile, getString(R.string.dashboard_game_folder_hint));
        setTileSubtitle(jarTile, getString(R.string.dashboard_run_jar_hint));
        setTileSubtitle(logsTile, getString(R.string.dashboard_share_logs_hint));

        mControlsTile.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mVersionSpinner.setOnInstanceSelectedListener(this::refreshInstanceSummary);
        refreshInstanceSummary();
    }

    private static View setupTile(View root, @IdRes int tileId, @DrawableRes int icon, @StringRes int title, View.OnClickListener listener) {
        View tile = root.findViewById(tileId);
        ((ImageView) tile.findViewById(R.id.dashboard_tile_icon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.dashboard_tile_title)).setText(title);
        tile.setOnClickListener(listener);
        return tile;
    }

    private static void setTileSubtitle(View tile, @Nullable CharSequence subtitle) {
        TextView subtitleView = tile.findViewById(R.id.dashboard_tile_subtitle);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
    }

    private void refreshInstanceSummary() {
        int generation = ++mSummaryGeneration;
        MojoApplication.sExecutorService.execute(()->{
            InstanceSummary summary = InstanceSummary.load();
            Tools.runOnUiThread(()->{
                if(getView() == null || generation != mSummaryGeneration) return;
                applyInstanceSummary(summary);
            });
        });
    }

    private void applyInstanceSummary(@Nullable InstanceSummary summary) {
        if(summary == null) {
            mInstanceDetails.setText(R.string.no_instance);
            mInstanceRenderer.setText(null);
            setTileSubtitle(mModsTile, null);
            setTileSubtitle(mControlsTile, null);
            return;
        }
        String loaderName = summary.platform != null ? ModTarget.getLoaderName(summary.platform) : getString(R.string.dashboard_vanilla);
        mInstanceDetails.setText(getString(R.string.dashboard_instance_details, loaderName, getVersionName(summary.mcVersion)));
        mInstanceRenderer.setText(getString(R.string.dashboard_renderer, getRendererName(summary.renderer)));
        setTileSubtitle(mControlsTile, getString(R.string.dashboard_controls_layout, summary.controlLayout));

        String modsText;
        if(summary.platform == null) {
            modsText = getString(R.string.dashboard_mods_no_loader);
        }else if(summary.modCount == 0) {
            modsText = getString(R.string.dashboard_mods_empty);
        }else {
            modsText = getResources().getQuantityString(R.plurals.mod_manager_mod_count, summary.modCount, summary.modCount);
            if(summary.disabledModCount > 0) modsText = getString(R.string.mod_manager_disabled_count, modsText, summary.disabledModCount);
        }
        setTileSubtitle(mModsTile, modsText);
        mModsTile.setAlpha(summary.platform == null ? UNAVAILABLE_TILE_ALPHA : 1f);
    }

    private String getVersionName(@Nullable String version) {
        if(version == null) return "?";
        if(Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(version)) return getString(R.string.profiles_latest_release);
        if(Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(version)) return getString(R.string.profiles_latest_snapshot);
        return version;
    }

    private String getRendererName(@Nullable String rendererId) {
        RenderSpec renderSpec = rendererId != null ? GameRenderer.getKnownRenderer(rendererId) : null;
        if(renderSpec == null) return String.valueOf(rendererId);
        String name = getString(renderSpec.displayName());
        int detailsStart = name.indexOf(" (");
        return detailsStart > 0 ? name.substring(0, detailsStart) : name;
    }

    private void openModManager(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        if(ModTarget.detectPlatform(instance) == null) {
            Toast.makeText(context, R.string.main_manage_mods_no_loader, Toast.LENGTH_LONG).show();
            return;
        }
        Tools.swapFragment(requireActivity(), ModManagerFragment.class, ModManagerFragment.TAG, null);
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }

    private static class InstanceSummary {
        @Nullable Platform platform;
        @Nullable String mcVersion;
        String renderer;
        String controlLayout;
        int modCount;
        int disabledModCount;

        @Nullable
        static InstanceSummary load() {
            Instance instance = Instances.loadSelectedInstance();
            if(instance == null) return null;
            InstanceSummary summary = new InstanceSummary();
            summary.platform = ModTarget.detectPlatform(instance);
            summary.mcVersion = ModTarget.detectMinecraftVersion(instance.versionId);
            if(summary.mcVersion == null) summary.mcVersion = instance.versionId;
            summary.renderer = instance.getLaunchRenderer();
            summary.controlLayout = FileUtils.removeExtension(new File(instance.getLaunchControls()).getName());
            if(summary.platform == null) return summary;
            File[] mods = new File(instance.getGameDirectory(), "mods").listFiles((dir, name) -> ModInfo.isModFileName(name));
            if(mods == null) return summary;
            for(File mod : mods) {
                summary.modCount++;
                if(ModInfo.isDisabledFileName(mod.getName())) summary.disabledModCount++;
            }
            return summary;
        }
    }
}
