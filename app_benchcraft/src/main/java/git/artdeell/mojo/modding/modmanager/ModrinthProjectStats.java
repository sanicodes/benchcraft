package git.artdeell.mojo.modding.modmanager;

/**
 * Popularity and dates of a Modrinth project, from the "projects" endpoint.
 */
public class ModrinthProjectStats {
    public String id;
    public long downloads;
    public int followers;
    public String published;
    public String updated;
}
