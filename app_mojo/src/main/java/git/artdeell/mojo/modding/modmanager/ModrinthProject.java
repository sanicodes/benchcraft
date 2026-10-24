package git.artdeell.mojo.modding.modmanager;

import git.artdeell.mojo.modding.modpacks.models.Constants;

/**
 * A search hit from the Modrinth v2 API.
 */
public class ModrinthProject {
    public String project_id;
    public String slug;
    public String title;
    public String description;
    public String author;
    public String icon_url;
    public long downloads;

    public String getIconCacheTag() {
        return Constants.SOURCE_MODRINTH + "_" + project_id;
    }
}
