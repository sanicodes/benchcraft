package git.artdeell.mojo.modding.modmanager;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import git.artdeell.mojo.utils.FileUtils;

public abstract class ModInfo {
    private static final String DISABLED_SUFFIX = ".disabled";
    public File jarFile;
    public final String searchTerms;
    public boolean expanded;
    @Nullable public ModrinthProjectStats modrinthStats;

    protected ModInfo(File jarFile, String searchTerms) {
        this.jarFile = jarFile;
        this.searchTerms = FileUtils.removeExtension(jarFile.getName().toLowerCase()) + searchTerms;
    }

    public static boolean isModFileName(String name) {
        return name.endsWith(".jar") || name.endsWith(".jar" + DISABLED_SUFFIX);
    }

    public boolean isEnabled() {
        return !jarFile.getName().endsWith(DISABLED_SUFFIX);
    }

    /**
     * Enable or disable the mod by adding or removing the ".disabled" suffix, which mod loaders skip.
     */
    public void setEnabled(boolean enabled) throws IOException {
        if(enabled == isEnabled()) return;
        String name = jarFile.getName();
        String newName = enabled ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name + DISABLED_SUFFIX;
        File newFile = new File(jarFile.getParentFile(), newName);
        if(newFile.exists() || !jarFile.renameTo(newFile)) throw new IOException("Failed to rename "+name+" to "+newName);
        jarFile = newFile;
        onFileMoved(newFile);
    }

    public String getName() {
        return getDisplayFileName();
    }

    public String getDisplayFileName() {
        String name = jarFile.getName();
        if(name.endsWith(DISABLED_SUFFIX)) return name.substring(0, name.length() - DISABLED_SUFFIX.length());
        return name;
    }

    protected void onFileMoved(File newFile) throws IOException {}
}
