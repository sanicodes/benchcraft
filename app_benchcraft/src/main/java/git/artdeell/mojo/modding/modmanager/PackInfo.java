package git.artdeell.mojo.modding.modmanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A resource pack or shader pack, either a zip file or a folder.
 */
public class PackInfo extends ModInfo {
    private static final long MAX_METADATA_SIZE = 8 * 1024 * 1024;
    private static final int ICON_SIZE = 128;

    private final String name;
    public final ContentType type;
    @Nullable public final String description;
    public final boolean isDirectory;
    public final long size;

    private PackInfo(File file, ContentType type, String name, @Nullable String description, boolean isDirectory, long size) {
        super(file, toSearchTerms(name, description));
        this.name = name;
        this.type = type;
        this.description = description;
        this.isDirectory = isDirectory;
        this.size = size;
    }

    public static PackInfo read(File file, ContentType type) {
        boolean isDirectory = file.isDirectory();
        String name = removeDisabledSuffix(file.getName());
        String extension = "." + type.fileExtension;
        if(!isDirectory && name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            name = name.substring(0, name.length() - extension.length());
        }
        return new PackInfo(file, type, name, readDescription(file), isDirectory, isDirectory ? 0 : file.length());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean canToggle() {
        return !isDirectory;
    }

    @Nullable
    public Bitmap loadIcon() {
        byte[] data = readEntry(jarFile, "pack.png");
        if(data == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while(Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= ICON_SIZE) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    @Nullable
    private static String readDescription(File file) {
        byte[] data = readEntry(file, "pack.mcmeta");
        if(data == null) return null;
        try {
            JsonObject pack = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            if(pack == null) return null;
            StringBuilder description = new StringBuilder();
            appendText(pack.get("description"), description);
            // Strip Minecraft formatting codes like §a
            String text = description.toString().replaceAll("§.", "").trim();
            return text.isEmpty() ? null : text;
        }catch (RuntimeException e) {
            return null;
        }
    }

    private static void appendText(@Nullable JsonElement element, StringBuilder builder) {
        if(element == null || element.isJsonNull()) return;
        if(element.isJsonPrimitive()) {
            builder.append(element.getAsString());
        }else if(element.isJsonArray()) {
            for(JsonElement child : element.getAsJsonArray()) appendText(child, builder);
        }else if(element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            appendText(object.get("text"), builder);
            appendText(object.get("extra"), builder);
        }
    }

    @Nullable
    private static byte[] readEntry(File file, String entryName) {
        try {
            if(file.isDirectory()) {
                File entryFile = new File(file, entryName);
                if(!entryFile.isFile() || entryFile.length() > MAX_METADATA_SIZE) return null;
                try(InputStream inputStream = new FileInputStream(entryFile)) {
                    return IOUtils.toByteArray(inputStream);
                }
            }
            try(ZipFile zipFile = new ZipFile(file)) {
                ZipEntry entry = zipFile.getEntry(entryName);
                if(entry == null || entry.getSize() > MAX_METADATA_SIZE) return null;
                try(InputStream inputStream = zipFile.getInputStream(entry)) {
                    return IOUtils.toByteArray(inputStream);
                }
            }
        }catch (IOException e) {
            return null;
        }
    }

    private static String toSearchTerms(String name, @Nullable String description) {
        String terms = name;
        if(description != null) terms += " " + description;
        return terms.toLowerCase(Locale.ROOT);
    }
}
