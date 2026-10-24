package git.artdeell.mojo.modding.modmanager;

import java.io.File;

/**
 * Kinds of content the mod manager can install into an instance. Each one has its own folder.
 */
public enum ContentType {
    MODS("mods", "mod", "jar"),
    RESOURCE_PACKS("resourcepacks", "resourcepack", "zip"),
    SHADERS("shaderpacks", "shader", "zip");

    public final String directoryName;
    public final String modrinthProjectType;
    public final String fileExtension;

    ContentType(String directoryName, String modrinthProjectType, String fileExtension) {
        this.directoryName = directoryName;
        this.modrinthProjectType = modrinthProjectType;
        this.fileExtension = fileExtension;
    }

    /**
     * Mods are always jar files, while packs can also be plain folders.
     */
    public boolean isContentFile(File file) {
        String name = file.getName();
        if(name.startsWith(".")) return false;
        if(file.isDirectory()) return this != MODS;
        String fileName = ModInfo.isDisabledFileName(name) ? ModInfo.removeDisabledSuffix(name) : name;
        return fileName.endsWith("." + fileExtension);
    }
}
