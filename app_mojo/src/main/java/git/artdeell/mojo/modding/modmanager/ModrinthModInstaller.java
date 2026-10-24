package git.artdeell.mojo.modding.modmanager;

import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import git.artdeell.mojo.R;
import git.artdeell.mojo.downloader.Downloader;
import git.artdeell.mojo.downloader.TaskMetadata;
import git.artdeell.mojo.tasks.mojson.mirrors.DownloadMirror;
import git.artdeell.mojo.utils.FileUtils;

/**
 * Installs a version from Modrinth into the folder for its content type. Mods also get the mods they require.
 */
public class ModrinthModInstaller {

    public static class Result {
        public final int installedDependencies;
        public final int enabledDependencies;
        public final List<String> missingDependencies;

        Result(int installedDependencies, int enabledDependencies, List<String> missingDependencies) {
            this.installedDependencies = installedDependencies;
            this.enabledDependencies = enabledDependencies;
            this.missingDependencies = missingDependencies;
        }
    }

    public static Result install(ModrinthModApi api, ModTarget target, ContentType type, ModrinthVersion version, InstalledModIndex index) throws IOException, InterruptedException {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.mod_browser_resolving_dependencies);
        try {
            ArrayList<ModrinthVersion> versions = new ArrayList<>();
            ArrayList<String> missingDependencies = new ArrayList<>();
            ArrayList<String> installedDependencyIds = new ArrayList<>();
            if(type == ContentType.MODS) {
                collectVersions(api, target, version, index, versions, missingDependencies, installedDependencyIds);
            }else {
                versions.add(version);
            }

            File directory = target.getDirectory(type);
            FileUtils.ensureDirectory(directory);
            ArrayList<TaskMetadata> downloads = new ArrayList<>(versions.size());
            for(ModrinthVersion current : versions) {
                ModrinthVersion.VersionFile file = current.getPrimaryFile();
                if(file == null) throw new IOException("Version "+current.id+" has no files");
                downloads.add(new TaskMetadata(
                        getTargetFile(directory, file.filename),
                        new URL(file.url), file.size,
                        file.hashes != null ? file.hashes.sha1 : null,
                        DownloadMirror.DOWNLOAD_CLASS_NONE
                ));
            }
            new ModDownloader().download(downloads);

            File newFile = downloads.get(0).path;
            File oldFile = index.getFile(version.project_id);
            if(oldFile != null && !oldFile.equals(newFile)) oldFile.delete();
            for(int i = 0; i < versions.size(); i++) {
                ModrinthVersion current = versions.get(i);
                index.put(current.project_id, current.id, downloads.get(i).path);
            }
            int enabledDependencies = 0;
            for(String projectId : installedDependencyIds) {
                if(enableInstalledMod(index, projectId)) enabledDependencies++;
            }
            return new Result(versions.size() - 1, enabledDependencies, missingDependencies);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private static void collectVersions(ModrinthModApi api, ModTarget target, ModrinthVersion root, InstalledModIndex index,
                                        List<ModrinthVersion> versions, List<String> missingDependencies, List<String> installedDependencyIds) {
        HashSet<String> seenProjects = new HashSet<>();
        ArrayDeque<ModrinthVersion> queue = new ArrayDeque<>();
        seenProjects.add(root.project_id);
        queue.add(root);
        while(!queue.isEmpty()) {
            ModrinthVersion current = queue.poll();
            versions.add(current);
            if(current.dependencies == null) continue;
            for(ModrinthVersion.Dependency dependency : current.dependencies) {
                if(!ModrinthVersion.DEPENDENCY_REQUIRED.equals(dependency.dependency_type)) continue;
                if(dependency.project_id != null && seenProjects.contains(dependency.project_id)) continue;
                if(dependency.project_id != null && index.isInstalled(dependency.project_id)) {
                    if(seenProjects.add(dependency.project_id)) installedDependencyIds.add(dependency.project_id);
                    continue;
                }
                ModrinthVersion dependencyVersion = resolveDependency(api, target, dependency);
                if(dependencyVersion == null) {
                    if(dependency.project_id != null) missingDependencies.add(getDisplayName(api, dependency.project_id));
                    continue;
                }
                if(!seenProjects.add(dependencyVersion.project_id)) continue;
                if(index.isInstalled(dependencyVersion.project_id)) {
                    installedDependencyIds.add(dependencyVersion.project_id);
                    continue;
                }
                queue.add(dependencyVersion);
            }
        }
    }

    /**
     * Turn an installed mod back on if it was disabled, since something now needs it.
     * @return true if the mod was disabled and got enabled
     */
    private static boolean enableInstalledMod(InstalledModIndex index, String projectId) {
        File file = index.getFile(projectId);
        if(file == null || !ModInfo.isDisabledFileName(file.getName())) return false;
        File enabledFile = new File(file.getParentFile(), ModInfo.removeDisabledSuffix(file.getName()));
        if(enabledFile.exists() || !file.renameTo(enabledFile)) return false;
        index.put(projectId, index.getVersionId(projectId), enabledFile);
        return true;
    }

    @Nullable
    private static ModrinthVersion resolveDependency(ModrinthModApi api, ModTarget target, ModrinthVersion.Dependency dependency) {
        if(dependency.version_id != null) {
            ModrinthVersion pinned = api.getVersion(dependency.version_id);
            if(pinned != null) return pinned;
        }
        if(dependency.project_id == null) return null;
        ModrinthVersion[] candidates = api.getVersions(dependency.project_id, target, ContentType.MODS);
        return pickVersion(candidates);
    }

    /**
     * @return the newest release, or the newest version of any kind if there are no releases
     */
    @Nullable
    public static ModrinthVersion pickVersion(@Nullable ModrinthVersion[] candidates) {
        if(candidates == null || candidates.length == 0) return null;
        for(ModrinthVersion candidate : candidates) {
            if(candidate.isRelease()) return candidate;
        }
        return candidates[0];
    }

    private static String getDisplayName(ModrinthModApi api, String projectId) {
        String title = api.getProjectTitle(projectId);
        return title != null ? title : projectId;
    }

    private static File getTargetFile(File directory, String fileName) throws IOException {
        if(fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")) {
            throw new IOException("Bad file name: "+fileName);
        }
        return new File(directory, fileName);
    }

    private static class ModDownloader extends Downloader {
        ModDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        void download(ArrayList<TaskMetadata> downloads) throws IOException, InterruptedException {
            runDownloads(downloads);
        }
    }
}
