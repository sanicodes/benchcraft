package git.artdeell.mojo.modding.modmanager;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;

import git.artdeell.mojo.JVersionList;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import me.andreasmelone.basicmodinfoparser.platform.Platform;

/**
 * Describes what an instance can load: its mod loader, Minecraft version and game folder.
 */
public class ModTarget {
    private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");
    @Nullable public final Platform platform;
    @Nullable public final String mcVersion;
    public final File gameDirectory;
    private final boolean mHasOptiFine;

    private ModTarget(@Nullable Platform platform, @Nullable String mcVersion, File gameDirectory, boolean hasOptiFine) {
        this.platform = platform;
        this.mcVersion = mcVersion;
        this.gameDirectory = gameDirectory;
        this.mHasOptiFine = hasOptiFine;
    }

    public static ModTarget fromInstance(Instance instance) {
        Platform platform = detectPlatform(instance);
        String mcVersion = detectMinecraftVersion(instance.versionId);
        if(platform == Platform.FORGE && isLegacyForgeVersion(mcVersion)) platform = Platform.FORGE_LEGACY;
        boolean hasOptiFine = instance.versionId != null && instance.versionId.toLowerCase(Locale.ROOT).contains("optifine");
        return new ModTarget(platform, mcVersion, instance.getGameDirectory(), hasOptiFine);
    }

    /**
     * Guess the mod loader from the version ID, since instances made before the mod manager
     * existed don't have a platform set.
     */
    @Nullable
    public static Platform detectPlatform(Instance instance) {
        if(instance.versionId != null) {
            String versionId = instance.versionId.toLowerCase(Locale.ROOT);
            if(versionId.contains("neoforge")) return Platform.NEOFORGE;
            if(versionId.contains("quilt")) return Platform.QUILT;
            if(versionId.contains("fabric")) return Platform.FABRIC;
            if(versionId.contains("forge")) return Platform.FORGE;
        }
        return instance.platform;
    }

    /**
     * Read the Minecraft version from the version file. Instances that were never launched don't have
     * that file yet, so a plain release ID like "1.21.1" is used as is.
     */
    @Nullable
    public static String detectMinecraftVersion(String versionId) {
        if(versionId == null) return null;
        try {
            JVersionList.Version version = Tools.getVersionInfo(versionId, true);
            if(version != null) return Tools.isValidString(version.inheritsFrom) ? version.inheritsFrom : version.id;
        }catch (RuntimeException e) {
            Log.w("ModTarget", "Failed to read version info for "+versionId, e);
        }
        return RELEASE_VERSION.matcher(versionId).matches() ? versionId : null;
    }

    private static boolean isLegacyForgeVersion(@Nullable String mcVersion) {
        if(mcVersion == null) return false;
        String[] parts = mcVersion.split("\\.");
        if(parts.length < 2 || !parts[0].equals("1")) return false;
        try {
            return Integer.parseInt(parts[1]) < 13;
        }catch (NumberFormatException e) {
            return false;
        }
    }

    public File getDirectory(ContentType type) {
        return new File(gameDirectory, type.directoryName);
    }

    /**
     * Mods need a mod loader and shaders need a mod loader for Iris/Oculus or OptiFine.
     * Resource packs work everywhere.
     */
    public boolean supports(ContentType type) {
        switch (type) {
            case MODS: return platform != null;
            case SHADERS: return platform != null || mHasOptiFine;
            default: return true;
        }
    }

    /**
     * Check if something that loads shaders is installed. Reads the mods folder.
     */
    public boolean hasShaderLoader() {
        if(mHasOptiFine) return true;
        File[] mods = getDirectory(ContentType.MODS).listFiles(ContentType.MODS::isContentFile);
        if(mods == null) return false;
        for(File mod : mods) {
            String name = mod.getName().toLowerCase(Locale.ROOT);
            if(ModInfo.isDisabledFileName(name)) continue;
            if(name.startsWith("iris") || name.startsWith("oculus") || name.contains("optifine")) return true;
        }
        return false;
    }

    /**
     * @return the shader mod to suggest for this loader
     */
    public String getShaderModName() {
        return platform == Platform.FORGE || platform == Platform.FORGE_LEGACY ? "Oculus" : "Iris";
    }

    /**
     * @return the loader names Modrinth uses for this content type. Quilt can also load Fabric mods.
     */
    public String[] getModrinthLoaders(ContentType type) {
        switch (type) {
            case RESOURCE_PACKS: return new String[]{"minecraft"};
            case SHADERS: return new String[]{"iris", "optifine"};
        }
        if(platform == null) return new String[0];
        switch (platform) {
            case FABRIC: return new String[]{"fabric"};
            case QUILT: return new String[]{"quilt", "fabric"};
            case NEOFORGE: return new String[]{"neoforge"};
            default: return new String[]{"forge"};
        }
    }

    public String getLoaderName() {
        if(platform == null) return mHasOptiFine ? "OptiFine" : "Vanilla";
        return getLoaderName(platform);
    }

    public static String getLoaderName(Platform platform) {
        switch (platform) {
            case FABRIC: return "Fabric";
            case QUILT: return "Quilt";
            case NEOFORGE: return "NeoForge";
            default: return "Forge";
        }
    }
}
