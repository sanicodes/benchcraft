package git.artdeell.mojo.modding.modmanager;

import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.commons.codec.binary.Hex;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import git.artdeell.mojo.utils.HashUtils;

/**
 * Maps Modrinth projects to the mod files that are already in a mods folder.
 */
public class InstalledModIndex {
    private final HashMap<String, File> mProjectFiles = new HashMap<>();
    private final HashMap<String, String> mProjectVersions = new HashMap<>();
    private final HashMap<File, String> mFileProjects = new HashMap<>();

    private InstalledModIndex() {}

    public static InstalledModIndex empty() {
        return new InstalledModIndex();
    }

    /**
     * Hash every mod in the folder and ask Modrinth what they are. Mods that didn't come from
     * Modrinth are simply missing from the index.
     */
    public static InstalledModIndex build(ModrinthModApi api, File modsDirectory) {
        File[] files = modsDirectory.listFiles((dir, name) -> ModInfo.isModFileName(name));
        if(files == null) return new InstalledModIndex();
        InstalledModIndex index = build(api, Arrays.asList(files));
        return index != null ? index : new InstalledModIndex();
    }

    /**
     * @return the index, or null if Modrinth couldn't be reached
     */
    @Nullable
    public static InstalledModIndex build(ModrinthModApi api, Collection<File> files) {
        InstalledModIndex index = new InstalledModIndex();
        if(files.isEmpty()) return index;
        HashMap<String, File> filesByHash = new HashMap<>(files.size());
        for(File file : files) {
            String hash = sha1(file);
            if(hash != null) filesByHash.put(hash, file);
        }
        Map<String, ModrinthVersion> versions = api.identifyFiles(filesByHash.keySet());
        if(versions == null) return null;
        for(Map.Entry<String, ModrinthVersion> entry : versions.entrySet()) {
            File file = filesByHash.get(entry.getKey());
            ModrinthVersion version = entry.getValue();
            if(file == null || version == null || version.project_id == null) continue;
            index.put(version.project_id, version.id, file);
        }
        return index;
    }

    synchronized void put(String projectId, String versionId, File file) {
        mProjectFiles.put(projectId, file);
        mProjectVersions.put(projectId, versionId);
        mFileProjects.put(file, projectId);
    }

    public synchronized Set<String> getProjectIds() {
        return new HashSet<>(mProjectFiles.keySet());
    }

    @Nullable
    public synchronized String getProjectId(File file) {
        return mFileProjects.get(file);
    }

    public synchronized boolean isInstalled(String projectId) {
        return mProjectFiles.containsKey(projectId);
    }

    @Nullable
    public synchronized File getFile(String projectId) {
        return mProjectFiles.get(projectId);
    }

    @Nullable
    public synchronized String getVersionId(String projectId) {
        return mProjectVersions.get(projectId);
    }

    @Nullable
    private static String sha1(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return Hex.encodeHexString(HashUtils.fileHash(digest, file));
        }catch (IOException | NoSuchAlgorithmException e) {
            Log.w("InstalledModIndex", "Failed to hash "+file.getName(), e);
            return null;
        }
    }
}
