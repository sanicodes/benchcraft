package git.artdeell.mojo.modding.modmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.modding.modpacks.SelfReferencingFuture;
import git.artdeell.mojo.utils.FilteredSubList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import me.andreasmelone.basicmodinfoparser.modfile.ModFile;
import me.andreasmelone.basicmodinfoparser.platform.BasicModInfo;
import me.andreasmelone.basicmodinfoparser.platform.Platform;
import me.andreasmelone.basicmodinfoparser.platform.dependency.version.Version;

public class ModAdapter extends RecyclerView.Adapter<ModAdapter.ViewHolder> {
    private static final float DISABLED_ALPHA = 0.5f;
    private final ExecutorService mIconLoadingService = Executors.newFixedThreadPool(2);
    private final List<ModInfo> mModInfos;
    private final FilteredSubList<ModInfo> mDisplayModInfos;
    private final Platform mPlatform;
    private final ModListListener mListener;
    private FilteredSubList.BasicPredicate<ModInfo> mSearchPredicate = i -> true;

    public ModAdapter(List<ModInfo> modInfos, Platform mPlatform, ModListListener listener)  {
        this.mModInfos = modInfos;
        this.mPlatform = mPlatform;
        this.mListener = listener;
        this.mDisplayModInfos = new FilteredSubList<>(modInfos, i->true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void performSearch(FilteredSubList.BasicPredicate<ModInfo> mModInfoPredicate) {
        mSearchPredicate = mModInfoPredicate;
        mDisplayModInfos.refresh(mModInfos, mModInfoPredicate);
        notifyDataSetChanged();
        mListener.onModListChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void sort(Comparator<ModInfo> comparator) {
        Collections.sort(mModInfos, comparator);
        mDisplayModInfos.refresh(mModInfos, mSearchPredicate);
        notifyDataSetChanged();
    }

    public List<ModInfo> getMods() {
        return new ArrayList<>(mModInfos);
    }

    public int getModCount() {
        return mModInfos.size();
    }

    public int getDisabledModCount() {
        int count = 0;
        for(ModInfo modInfo : mModInfos) {
            if(!modInfo.isEnabled()) count++;
        }
        return count;
    }

    public int getDisplayedModCount() {
        return mDisplayModInfos.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mDisplayModInfos.get(position).expanded ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View holderLayout = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_mod_modmanager, parent, false);
        switch (viewType) {
            case 0: return new ViewHolder(holderLayout);
            case 1: return new ExpandedViewHolder(holderLayout);
            default: throw new RuntimeException("Unknown viewType");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mDisplayModInfos.get(position));
    }

    @Override
    public int getItemCount() {
        return mDisplayModInfos.size();
    }

    private void removeMod(ModInfo modInfo) {
        int position = mDisplayModInfos.indexOf(modInfo);
        mModInfos.remove(modInfo);
        mDisplayModInfos.remove(modInfo);
        if(position != -1) notifyItemRemoved(position);
        mListener.onModListChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final ImageView mThumbnail;
        private final TextView mName;
        private final TextView mDescription;
        private final TextView mVersion;
        private final SwitchCompat mEnableSwitch;
        private final ColorStateList mDefaultNameColor;
        private final ColorStateList mDefaultDescriptionColor;
        private Future<?> mIconLoadingFuture;
        protected ModInfo mModInfo;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mThumbnail = itemView.findViewById(R.id.mod_thumbnail_imageview);
            mName = itemView.findViewById(R.id.mod_title_textview);
            mDescription = itemView.findViewById(R.id.mod_body_textview);
            mVersion = itemView.findViewById(R.id.mod_version_textview);
            mEnableSwitch = itemView.findViewById(R.id.mod_manager_enable_switch);
            mDefaultNameColor = mName.getTextColors();
            mDefaultDescriptionColor = mDescription.getTextColors();
            itemView.setOnClickListener(this);
        }

        public void bind(ModInfo modInfo) {
            mModInfo = modInfo;
            if(mIconLoadingFuture != null) mIconLoadingFuture.cancel(true);
            if(modInfo instanceof ContainedModInfo) bindContained((ContainedModInfo) modInfo);
            else if(modInfo instanceof CorruptModInfo) bindCorrupt((CorruptModInfo) modInfo);
            mEnableSwitch.setOnCheckedChangeListener(null);
            mEnableSwitch.setChecked(modInfo.isEnabled());
            mEnableSwitch.setOnCheckedChangeListener((v, checked) -> toggleMod(modInfo, checked));
            float alpha = modInfo.isEnabled() ? 1f : DISABLED_ALPHA;
            mThumbnail.setAlpha(alpha);
            mName.setAlpha(alpha);
            mVersion.setAlpha(alpha);
            mDescription.setAlpha(alpha);
        }

        private void toggleMod(ModInfo modInfo, boolean enabled) {
            mEnableSwitch.setEnabled(false);
            Context context = itemView.getContext();
            MojoApplication.sExecutorService.execute(() -> {
                boolean success = true;
                try {
                    modInfo.setEnabled(enabled);
                }catch (IOException e) {
                    Log.w("ModAdapter", "Failed to toggle mod", e);
                    success = false;
                }
                boolean finalSuccess = success;
                Tools.runOnUiThread(() -> {
                    if(!finalSuccess) Toast.makeText(context, R.string.mod_manager_toggle_failed, Toast.LENGTH_SHORT).show();
                    mEnableSwitch.setEnabled(true);
                    if(mModInfo == modInfo) bind(modInfo);
                    mListener.onModListChanged();
                });
            });
        }

        private void setBitmapThumbnail(Bitmap bitmap) {
            if(bitmap == null) {
                mThumbnail.setVisibility(View.GONE);
                return;
            }else {
                mThumbnail.setVisibility(View.VISIBLE);
            }
            Drawable drawable = mThumbnail.getDrawable();
            Bitmap lastBitmap = null;
            if(drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable)drawable;
                lastBitmap = bitmapDrawable.getBitmap();
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bitmapDrawable.setBitmap(bitmap);
                }else {
                    mThumbnail.setImageBitmap(bitmap);
                }
            }else {
                mThumbnail.setImageBitmap(bitmap);
            }
            if(lastBitmap != null && lastBitmap != bitmap && !lastBitmap.isRecycled()) lastBitmap.recycle();
        }

        private void applyThumbnail(final ModFile modFile) {
            mIconLoadingFuture = new SelfReferencingFuture(f->{
                Bitmap iconBitmap = null;
                try(InputStream inputStream = modFile.getIconStream()) {
                    if(inputStream != null) iconBitmap = BitmapFactory.decodeStream(inputStream);
                }catch (IOException e) {
                    Log.w("ModAdapter", "Failed to load icon", e);
                }
                Bitmap finalIcon = iconBitmap;
                Tools.runOnUiThread(()->{
                    if(f.isCancelled()) return;
                    setBitmapThumbnail(finalIcon);
                });
            }).startOnExecutor(mIconLoadingService);
        }

        private void bindContained(ContainedModInfo containedModInfo) {
            mName.setTextColor(mDefaultNameColor);
            mDescription.setTextColor(mDefaultDescriptionColor);
            BasicModInfo basicModInfo = containedModInfo.modFile.getInfo(mPlatform)[0];
            String name = basicModInfo.getName();
            String description = basicModInfo.getDescription();
            Version<?> version = basicModInfo.getVersion();
            if(name == null) name = containedModInfo.getDisplayFileName();
            mName.setText(name);
            if(description == null) mDescription.setText(R.string.mod_manager_mod_unknown_description);
            else mDescription.setText(description.trim());
            if(version == null) mVersion.setText(R.string.mod_manager_mod_unknown_version);
            else mVersion.setText(version.toString());
            applyThumbnail(containedModInfo.modFile);
        }

        private void bindCorrupt(CorruptModInfo corruptModInfo) {
            mName.setTextColor(Color.RED);
            mDescription.setTextColor(Color.RED);
            int corruptionMsgRes;
            switch (corruptModInfo.corruptionReason) {
                case CorruptModInfo.CORRUPTION_REASON_NOT_READABLE:
                    corruptionMsgRes = R.string.mod_manager_mod_corrupt_cant_read;
                    break;
                case CorruptModInfo.CORRUPTION_REASON_NOT_A_MOD:
                    corruptionMsgRes = R.string.mod_manager_mod_corrupt_not_a_mod;
                    break;
                default:
                    throw new RuntimeException("Unknown corruption reason");
            }
            mName.setText(corruptModInfo.getDisplayFileName());
            mDescription.setText(corruptionMsgRes);
            mVersion.setText(R.string.mod_manager_mod_corrupt);
            mThumbnail.setVisibility(View.GONE);
        }

        @Override
        public void onClick(View view) {
            mModInfo.expanded = !mModInfo.expanded;
            int position = getAdapterPosition();
            if(position != RecyclerView.NO_POSITION) notifyItemChanged(position);
        }
    }

    private class ExpandedViewHolder extends ViewHolder {
        private final TextView mFileName;
        public ExpandedViewHolder(@NonNull View itemView) {
            super(itemView);
            View expansionView = ((ViewStub) itemView.findViewById(R.id.mod_limited_state_stub)).inflate();
            mFileName = expansionView.findViewById(R.id.mod_name_textview);
            Button delete = expansionView.findViewById(R.id.mod_manager_delete_button);
            delete.setOnClickListener(v -> confirmDelete(v.getContext(), mModInfo));
        }

        @Override
        public void bind(ModInfo modInfo) {
            super.bind(modInfo);
            mFileName.setText(modInfo.jarFile.getName());
        }

        private void confirmDelete(Context context, ModInfo modInfo) {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.mod_manager_delete_title, modInfo.getDisplayFileName()))
                    .setMessage(R.string.mod_manager_delete_message)
                    .setPositiveButton(R.string.global_delete, (d, w) -> {
                        if(modInfo.jarFile.delete() || !modInfo.jarFile.exists()) removeMod(modInfo);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    public interface ModListListener {
        void onModListChanged();
    }
}
