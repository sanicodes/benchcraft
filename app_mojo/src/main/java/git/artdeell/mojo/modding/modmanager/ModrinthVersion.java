package git.artdeell.mojo.modding.modmanager;

import androidx.annotation.Nullable;

/**
 * A single version of a Modrinth project, as returned by the v2 API.
 */
public class ModrinthVersion {
    public static final String DEPENDENCY_REQUIRED = "required";

    public String id;
    public String project_id;
    public String name;
    public String version_number;
    public String version_type;
    public String date_published;
    public String[] game_versions;
    public String[] loaders;
    public VersionFile[] files;
    public Dependency[] dependencies;

    @Nullable
    public VersionFile getPrimaryFile() {
        if(files == null || files.length == 0) return null;
        for(VersionFile file : files) {
            if(file.primary) return file;
        }
        return files[0];
    }

    public boolean isRelease() {
        return "release".equals(version_type);
    }

    public static class VersionFile {
        public String url;
        public String filename;
        public boolean primary;
        public long size;
        public Hashes hashes;
    }

    public static class Hashes {
        public String sha1;
        public String sha512;
    }

    public static class Dependency {
        public String version_id;
        public String project_id;
        public String file_name;
        public String dependency_type;
    }
}
