package git.artdeell.mojo.modding.modmanager;

import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.modding.modpacks.api.ApiHandler;
import git.artdeell.mojo.utils.DownloadUtils;

/**
 * Modrinth API calls for single mods, resource packs and shaders. Modpacks are handled by ModrinthApi.
 */
public class ModrinthModApi {
    public static final int PAGE_SIZE = 30;
    public static final String SORT_RELEVANCE = "relevance";
    public static final String SORT_DOWNLOADS = "downloads";
    public static final String SORT_FOLLOWS = "follows";
    public static final String SORT_NEWEST = "newest";
    public static final String SORT_UPDATED = "updated";
    private static final int PROJECTS_PER_REQUEST = 100;

    private final ApiHandler mApiHandler;

    public ModrinthModApi() {
        ArrayMap<String, String> headers = new ArrayMap<>();
        headers.put("User-Agent", DownloadUtils.USER_AGENT);
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2", headers);
    }

    @Nullable
    public SearchPage search(String query, ModTarget target, ContentType type, String sortIndex, int offset) {
        JsonArray facets = new JsonArray();
        facets.add(toJsonArray("project_type:" + type.modrinthProjectType));
        if(type != ContentType.RESOURCE_PACKS) {
            JsonArray loaderFacet = new JsonArray();
            for(String loader : target.getModrinthLoaders(type)) loaderFacet.add("categories:" + loader);
            facets.add(loaderFacet);
        }
        if(target.mcVersion != null) facets.add(toJsonArray("versions:" + target.mcVersion));

        HashMap<String, Object> params = new HashMap<>();
        params.put("query", query == null ? "" : query);
        params.put("facets", facets.toString());
        params.put("index", sortIndex);
        params.put("limit", PAGE_SIZE);
        params.put("offset", offset);
        return mApiHandler.get("search", params, SearchPage.class);
    }

    /**
     * @return versions of the project that work with the target, newest first
     */
    @Nullable
    public ModrinthVersion[] getVersions(String projectId, ModTarget target, ContentType type) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("loaders", toJsonArray(target.getModrinthLoaders(type)).toString());
        if(target.mcVersion != null) params.put("game_versions", toJsonArray(target.mcVersion).toString());
        return mApiHandler.get("project/" + projectId + "/version", params, ModrinthVersion[].class);
    }

    @Nullable
    public ModrinthVersion getVersion(String versionId) {
        return mApiHandler.get("version/" + versionId, ModrinthVersion.class);
    }

    @Nullable
    public String getProjectTitle(String projectId) {
        JsonObject project = mApiHandler.get("project/" + projectId, JsonObject.class);
        if(project == null || !project.has("title")) return null;
        return project.get("title").getAsString();
    }

    /**
     * Find out which Modrinth versions the given files belong to.
     * @param sha1Hashes SHA-1 hashes of the files
     * @return a map from hash to version. Files that Modrinth doesn't know are left out.
     */
    @Nullable
    public Map<String, ModrinthVersion> identifyFiles(Collection<String> sha1Hashes) {
        HashMap<String, ModrinthVersion> result = new HashMap<>();
        if(sha1Hashes.isEmpty()) return result;
        JsonObject body = new JsonObject();
        JsonArray hashes = new JsonArray();
        for(String hash : sha1Hashes) hashes.add(hash);
        body.add("hashes", hashes);
        body.addProperty("algorithm", "sha1");
        JsonObject response = mApiHandler.post("version_files", body, JsonObject.class);
        if(response == null) return null;
        for(Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), Tools.GLOBAL_GSON.fromJson(entry.getValue(), ModrinthVersion.class));
        }
        return result;
    }

    /**
     * @return a map from project ID to its stats, or null if Modrinth couldn't be reached
     */
    @Nullable
    public Map<String, ModrinthProjectStats> getProjectStats(Collection<String> projectIds) {
        HashMap<String, ModrinthProjectStats> result = new HashMap<>();
        String[] ids = projectIds.toArray(new String[0]);
        for(int start = 0; start < ids.length; start += PROJECTS_PER_REQUEST) {
            String[] chunk = Arrays.copyOfRange(ids, start, Math.min(ids.length, start + PROJECTS_PER_REQUEST));
            HashMap<String, Object> params = new HashMap<>();
            params.put("ids", toJsonArray(chunk).toString());
            ModrinthProjectStats[] projects = mApiHandler.get("projects", params, ModrinthProjectStats[].class);
            if(projects == null) return null;
            for(ModrinthProjectStats project : projects) result.put(project.id, project);
        }
        return result;
    }

    private static JsonArray toJsonArray(String... values) {
        JsonArray array = new JsonArray();
        for(String value : values) array.add(value);
        return array;
    }

    public static class SearchPage {
        public ModrinthProject[] hits;
        public int offset;
        public int total_hits;
    }
}
