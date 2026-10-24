package git.artdeell.mojo.fragments.instance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.fragments.misc.DeleteConfirmDialogFragment;
import git.artdeell.mojo.fragments.misc.FileSelectorFragment;
import git.artdeell.mojo.fragments.modding.ModManagerFragment;
import git.artdeell.mojo.game.renderer.RendererCache;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.multirt.MultiRTUtils;
import git.artdeell.mojo.multirt.RTSpinnerAdapter;
import git.artdeell.mojo.multirt.Runtime;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.utils.profiles.VersionSelectorDialog;
import git.artdeell.mojo.utils.CropperUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstanceEditorFragment extends Fragment implements CropperUtils.CropperReceiver {
    public static final String TAG = "InstanceEditorFragment";

    private Instance mInstance;
    private String mSelectedControlLayout;
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mVersionSelectButton, mManageModsButton;
    private Button mIconUploadButton, mIconModrinthButton;
    private ImageButton mIconRemoveButton;
    private LinearLayout mIconPresetContainer;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultVersion, mDefaultControl, mVersionLabel, mSharedDataLabel;
    private ImageView mInstanceIcon;
    private CheckBox mSharedDataCheckbox;
    private int mRecommendedIconSize;
    /* Icon changes are kept here until the instance gets saved */
    private boolean mIconStateLoaded;
    private String mSelectedStaticIcon;
    private boolean mHasCustomIcon;
    private Bitmap mPendingCustomIcon;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private List<String> mRenderNames;

    public InstanceEditorFragment(){
        super(R.layout.fragment_instance_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if(value != null){
            mSelectedControlLayout = value;
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);

        RendererCache list = RendererCache.getCompatibleRenderers(view.getContext());
        mRenderNames = list.rendererIds;
        List<String> renderList = new ArrayList<>(list.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(list.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(view.getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            InstanceIconProvider.dropIcon(mInstance);
            save();
            Tools.backToMainMenu(requireActivity());
        });

        mDeleteButton.setOnClickListener(v -> {
            DeleteConfirmDialogFragment dialogFragment = new DeleteConfirmDialogFragment();
            dialogFragment.show(getChildFragmentManager(), "delete_dialog_confirm");
        });

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        // Set up the icon change click listeners
        View.OnClickListener iconUploadListener = v -> {
            // Fill recommended size on click to ge the most up to date data
            mRecommendedIconSize = Math.max(mInstanceIcon.getWidth(), mInstanceIcon.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        };
        mInstanceIcon.setOnClickListener(iconUploadListener);
        mIconUploadButton.setOnClickListener(iconUploadListener);
        mIconRemoveButton.setOnClickListener(v -> {
            mPendingCustomIcon = null;
            mHasCustomIcon = false;
            refreshIconViews();
        });
        mIconModrinthButton.setOnClickListener(v -> ModrinthIconPickerDialog.show(v.getContext(), icon -> {
            if(getView() != null) setPendingCustomIcon(icon);
        }));

        mSharedDataCheckbox.setOnCheckedChangeListener((v,checked) ->{
            mInstance.sharedData = checked;
            int text = R.string.instance_shared_data_off;
            if(checked) text = R.string.instance_shared_data_on;
            mSharedDataCheckbox.setText(text);
        });

        mManageModsButton.setOnClickListener(v -> {
            Tools.swapFragment(requireActivity(), ModManagerFragment.class, ModManagerFragment.TAG, null);
        });

        Instance selectedInstance = Instances.loadSelectedInstance();
        Context context = view.getContext();
        if(selectedInstance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            getParentFragmentManager().popBackStack();
        }else {
            loadValues(selectedInstance, context);
        }
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, (id, snapshot)-> mDefaultVersion.setText(id));
    }

    private static String nullToEmpty(String in) {
        if(in == null) return "";
        return in;
    }

    private void loadValues(@NonNull Instance instance, @NonNull Context context){
        mInstance = instance;
        // Keep unsaved icon changes when coming back from another screen (e.g. the control selector)
        if(!mIconStateLoaded) {
            mSelectedStaticIcon = instance.icon;
            mHasCustomIcon = instance.hasCustomIcon();
            mIconStateLoaded = true;
        }
        createIconPresets(context);
        refreshIconViews();
        if(instance.platform != null) {
            mVersionSelectButton.setVisibility(View.GONE);
            mDefaultVersion.setVisibility(View.GONE);
            mVersionLabel.setVisibility(View.GONE);
            mSharedDataCheckbox.setVisibility(View.GONE);
            mSharedDataLabel.setVisibility(View.GONE);
        }

        // Runtime spinner
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        int jvmIndex = -1;
        if(instance.selectedRuntime != null) {
            jvmIndex = runtimes.indexOf(new Runtime(instance.selectedRuntime));
        }
        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
        int rendererIndex = mRenderNames.indexOf(instance.getLaunchRenderer());
        if(rendererIndex == -1) {
            rendererIndex = mDefaultRenderer.getAdapter().getCount() - 1;
        }
        mDefaultRenderer.setSelection(rendererIndex);

        mDefaultVersion.setText(instance.versionId);
        mDefaultJvmArgument.setText(nullToEmpty(instance.jvmArgs));
        mDefaultName.setText(nullToEmpty(instance.name));
        mDefaultControl.setText(mSelectedControlLayout == null ? nullToEmpty(instance.controlLayout) : mSelectedControlLayout);
        mSharedDataCheckbox.setChecked(instance.sharedData);
    }

    private void createIconPresets(@NonNull Context context) {
        mIconPresetContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for(String iconName : InstanceIconProvider.getStaticIconNames()) {
            ImageView presetView = (ImageView) inflater.inflate(R.layout.item_instance_icon_preset, mIconPresetContainer, false);
            presetView.setImageDrawable(InstanceIconProvider.getStaticIconDrawable(getResources(), iconName));
            presetView.setContentDescription(getString(InstanceIconProvider.getStaticIconLabel(iconName)));
            presetView.setTag(iconName);
            presetView.setOnClickListener(v -> {
                mSelectedStaticIcon = iconName;
                mPendingCustomIcon = null;
                mHasCustomIcon = false;
                refreshIconViews();
            });
            mIconPresetContainer.addView(presetView);
        }
    }

    private void refreshIconViews() {
        Drawable icon;
        if(mPendingCustomIcon != null) icon = InstanceIconProvider.createCustomIcon(getResources(), mPendingCustomIcon);
        else if(mHasCustomIcon) icon = InstanceIconProvider.fetchIcon(getResources(), mInstance);
        else icon = InstanceIconProvider.getStaticIconDrawable(getResources(), mSelectedStaticIcon);
        mInstanceIcon.setImageDrawable(icon);
        mIconRemoveButton.setVisibility(mHasCustomIcon ? View.VISIBLE : View.GONE);
        for(int i = 0; i < mIconPresetContainer.getChildCount(); i++) {
            View presetView = mIconPresetContainer.getChildAt(i);
            presetView.setSelected(!mHasCustomIcon && presetView.getTag().equals(mSelectedStaticIcon));
        }
    }

    private void bindViews(@NonNull View view){
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_instance_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);
        mVersionLabel = view.findViewById(R.id.vprof_editor_version_sub);

        mDefaultName = view.findViewById(R.id.vprof_editor_instance_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mInstanceIcon = view.findViewById(R.id.vprof_editor_instance_icon);
        mSharedDataCheckbox = view.findViewById(R.id.vprof_editor_data_checkbox_container);
        mSharedDataLabel = view.findViewById(R.id.vprof_editor_data_checkbox_sub);
        mManageModsButton = view.findViewById(R.id.vprof_editor_manage_mods);
        mIconUploadButton = view.findViewById(R.id.vprof_editor_icon_upload);
        mIconRemoveButton = view.findViewById(R.id.vprof_editor_icon_remove);
        mIconModrinthButton = view.findViewById(R.id.vprof_editor_icon_modrinth);
        mIconPresetContainer = view.findViewById(R.id.vprof_editor_icon_presets);
    }

    private void save(){
        //First, check for potential issues in the inputs
        mInstance.versionId = mDefaultVersion.getText().toString();
        mInstance.controlLayout = mDefaultControl.getText().toString();
        mInstance.jvmArgs = mDefaultJvmArgument.getText().toString();

        String newName = mDefaultName.getText().toString();
        if(mInstance.controlLayout.isEmpty()) mInstance.controlLayout = null;
        if(mInstance.jvmArgs.isEmpty()) mInstance.jvmArgs = null;

        Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
        mInstance.selectedRuntime = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                ? null : selectedRuntime.name;

        if(mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size()) mInstance.renderer = null;
        else mInstance.renderer = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());

        try {
            if(!newName.isEmpty() && !newName.equals(mInstance.name))
                Instances.renameInstanceDirectory(mInstance, newName);
            mInstance.name = newName;
            saveIcon();
            mInstance.write();
        }catch (Exception e) {
            Tools.showErrorRemote(e);
        }
    }

    /** Apply the icon changes. Runs after a rename, so that the icon file ends up in the new directory. */
    private void saveIcon() throws IOException {
        mInstance.icon = mSelectedStaticIcon;
        if(mPendingCustomIcon != null) mInstance.encodeNewIcon(mPendingCustomIcon);
        else if(!mHasCustomIcon) mInstance.removeCustomIcon();
    }

    @Override
    public float getAspectRatio() {
        return 1f;
    }

    @Override
    public int getTargetMaxSide() {
        return mRecommendedIconSize;
    }

    /** Show a new custom icon. It's only written to the instance on save. */
    private void setPendingCustomIcon(Bitmap icon) {
        mPendingCustomIcon = icon;
        mHasCustomIcon = true;
        refreshIconViews();
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        setPendingCustomIcon(contentBitmap);
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
