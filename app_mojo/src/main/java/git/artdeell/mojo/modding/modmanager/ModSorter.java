package git.artdeell.mojo.modding.modmanager;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sort orders for the installed mods list. Apart from the name, they match the ones on Modrinth,
 * so mods that aren't on Modrinth always go last.
 */
public class ModSorter {
    public static final int SORT_NAME = 0;
    public static final int SORT_DOWNLOADS = 1;
    public static final int SORT_FOLLOWS = 2;
    public static final int SORT_NEWEST = 3;
    public static final int SORT_UPDATED = 4;
    private static final int DATE_LENGTH = "2000-01-01T00:00:00".length();

    public static boolean needsModrinthStats(int sort) {
        return sort != SORT_NAME;
    }

    public static Comparator<ModInfo> comparator(int sort) {
        Comparator<ModInfo> byName = (a, b) -> a.getName().compareToIgnoreCase(b.getName());
        if(!needsModrinthStats(sort)) return byName;
        return (a, b) -> {
            ModrinthProjectStats statsA = a.modrinthStats, statsB = b.modrinthStats;
            if(statsA == null || statsB == null) {
                if(statsA != statsB) return statsA == null ? 1 : -1;
                return byName.compare(a, b);
            }
            int result = compareStats(sort, statsA, statsB);
            return result != 0 ? result : byName.compare(a, b);
        };
    }

    private static int compareStats(int sort, ModrinthProjectStats a, ModrinthProjectStats b) {
        switch (sort) {
            case SORT_DOWNLOADS: return Long.compare(b.downloads, a.downloads);
            case SORT_FOLLOWS: return Integer.compare(b.followers, a.followers);
            case SORT_NEWEST: return compareDates(b.published, a.published);
            case SORT_UPDATED: return compareDates(b.updated, a.updated);
            default: return 0;
        }
    }

    private static int compareDates(String a, String b) {
        if(a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
        return trimDate(a).compareTo(trimDate(b));
    }

    // Modrinth dates are ISO 8601 in UTC with varying fractions of a second
    private static String trimDate(String date) {
        return date.length() > DATE_LENGTH ? date.substring(0, DATE_LENGTH) : date;
    }

    /**
     * Look up the mods on Modrinth and fill in their stats. Runs on the calling thread.
     * @return false if Modrinth couldn't be reached
     */
    public static boolean loadModrinthStats(ModrinthModApi api, List<ModInfo> mods) {
        HashMap<ModInfo, File> modFiles = new HashMap<>(mods.size());
        for(ModInfo mod : mods) modFiles.put(mod, mod.jarFile);
        InstalledModIndex index = InstalledModIndex.build(api, modFiles.values());
        if(index == null) return false;
        Map<String, ModrinthProjectStats> stats = api.getProjectStats(index.getProjectIds());
        if(stats == null) return false;
        for(Map.Entry<ModInfo, File> entry : modFiles.entrySet()) {
            String projectId = index.getProjectId(entry.getValue());
            entry.getKey().modrinthStats = projectId != null ? stats.get(projectId) : null;
        }
        return true;
    }
}
