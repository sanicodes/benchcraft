package git.artdeell.mojo.modding.modmanager;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;

import git.artdeell.mojo.JVersionList;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import me.andreasmelone.basicmodinfoparser.platform.Platform;

/**
 * Describes what kind of mods an instance can load: its mod loader, Minecraft version and mods folder.
 */
public class ModTarget {
    public final Platform platform;
    @Nullable public final String mcVersion;
    public final File modsDirectory;

    private ModTarget(Platform platform, @Nullable String mcVersion, File modsDirectory) {
        this.platform = platform;
        this.mcVersion = mcVersion;
        this.modsDirectory = modsDirectory;
    }

    /**
     * @return the target for this instance, or null if the instance has no mod loader
     */
    @Nullable
    public static ModTarget fromInstance(Instance instance) {
        Platform platform = detectPlatform(instance);
        if(platform == null) return null;
        String mcVersion = detectMinecraftVersion(instance.versionId);
        if(platform == Platform.FORGE && isLegacyForgeVersion(mcVersion)) platform = Platform.FORGE_LEGACY;
        return new ModTarget(platform, mcVersion, new File(instance.getGameDirectory(), "mods"));
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

    @Nullable
    public static String detectMinecraftVersion(String versionId) {
        if(versionId == null) return null;
        try {
            JVersionList.Version version = Tools.getVersionInfo(versionId, true);
            if(version == null) return null;
            if(Tools.isValidString(version.inheritsFrom)) return version.inheritsFrom;
            return version.id;
        }catch (RuntimeException e) {
            Log.w("ModTarget", "Failed to read version info for "+versionId, e);
            return null;
        }
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

    /**
     * @return the loader names Modrinth uses for this platform. Quilt can also load Fabric mods.
     */
    public String[] getModrinthLoaders() {
        switch (platform) {
            case FABRIC: return new String[]{"fabric"};
            case QUILT: return new String[]{"quilt", "fabric"};
            case NEOFORGE: return new String[]{"neoforge"};
            default: return new String[]{"forge"};
        }
    }

    public String getLoaderName() {
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
