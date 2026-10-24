package git.artdeell.mojo.instances;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import git.artdeell.mojo.R;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class InstanceIconProvider {
    public static final String FALLBACK_ICON_NAME = "default";
    private static final Map<Integer, Drawable> sIconCache = new HashMap<>();
    private static final Map<String, Drawable> sStaticIconCache = new HashMap<>();
    /** Static icons in the order they are offered in the icon picker */
    private static final Map<String, Integer> sStaticIcons = new LinkedHashMap<>();
    private static final Map<String, Integer> sStaticIconLabels = new HashMap<>();

    static {
        addStaticIcon("default", R.drawable.ic_mojo_full, R.string.instance_icon_default);
        addStaticIcon("crafting_table", R.drawable.ic_instance_crafting_table, R.string.instance_icon_crafting_table);
        addStaticIcon("fabric", R.drawable.ic_fabric, R.string.instance_icon_fabric);
        addStaticIcon("quilt", R.drawable.ic_quilt, R.string.instance_icon_quilt);
        addStaticIcon("forge", R.drawable.ic_forge, R.string.instance_icon_forge);
        addStaticIcon("neoforge", R.drawable.ic_neoforge, R.string.instance_icon_neoforge);
    }

    private static void addStaticIcon(String name, int drawable, @StringRes int label) {
        sStaticIcons.put(name, drawable);
        sStaticIconLabels.put(name, label);
    }

    /**
     * Fetch an icon from the cache, or load it if it's not cached.
     * @param resources the Resources object, used for creating drawables
     * @param instance the instance
     * @return an icon drawable
     */
    public static @NonNull Drawable fetchIcon(Resources resources, @NonNull DisplayInstance instance) {
        return copyIcon(resources, fetchCachedIcon(resources, instance));
    }

    private static @NonNull Drawable fetchCachedIcon(Resources resources, @NonNull DisplayInstance instance) {
        int identityHashCode = System.identityHashCode(instance);

        Drawable cachedIcon = sIconCache.get(identityHashCode);
        if(cachedIcon != null) return cachedIcon;

        Drawable instanceIcon = fetchInstanceFileIcon(resources, identityHashCode, instance.getInstanceIconLocation());
        if(instanceIcon != null) return instanceIcon;

        return fetchStaticIcon(resources, identityHashCode, instance.icon);
    }

    /**
     * Views resize the drawables they display, so a cached drawable shared between views ends up
     * drawn at whichever size was set last. Every caller gets a copy that shares the image data
     * but has its own bounds.
     */
    private static @NonNull Drawable copyIcon(Resources resources, @NonNull Drawable icon) {
        if(icon instanceof RoundedBitmapDrawable) {
            Bitmap iconBitmap = ((RoundedBitmapDrawable) icon).getBitmap();
            if(iconBitmap != null) return createCustomIcon(resources, iconBitmap);
        }
        Drawable.ConstantState iconState = icon.getConstantState();
        return iconState != null ? iconState.newDrawable(resources) : icon;
    }

    /**
     * Drop an icon from the icon cache. When dropped, it's Drawable will be re-read from the
     * instance icon file (or re-fetched from the static cache)
     * @param key the instance
     */
    public static void dropIcon(@NonNull Instance key) {
        sIconCache.remove(System.identityHashCode(key));
    }

    private static Drawable fetchInstanceFileIcon(Resources resources, int identityHash, File iconLocation) {
        if(!iconLocation.isFile() || !iconLocation.canRead()) return null;
        Bitmap iconBitmap = BitmapFactory.decodeFile(iconLocation.getAbsolutePath());
        if(iconBitmap == null) return null;
        Drawable iconDrawable = createCustomIcon(resources, iconBitmap);
        sIconCache.put(identityHash, iconDrawable);
        return iconDrawable;
    }

    /**
     * Wrap a custom icon image into a drawable with rounded corners, like the rest of the launcher.
     * @param resources the Resources object, used for creating drawables
     * @param iconBitmap the icon image
     * @return the icon drawable
     */
    public static @NonNull Drawable createCustomIcon(Resources resources, @NonNull Bitmap iconBitmap) {
        RoundedBitmapDrawable iconDrawable = RoundedBitmapDrawableFactory.create(resources, iconBitmap);
        iconDrawable.setCornerRadius(Math.min(iconBitmap.getWidth(), iconBitmap.getHeight()) * 0.2f);
        iconDrawable.setAntiAlias(true);
        return iconDrawable;
    }

    private static Drawable fetchStaticIcon(Resources resources, int identityHash, String icon) {
        Drawable staticIcon = sStaticIconCache.get(icon);
        if(staticIcon == null) {
            if(icon != null) staticIcon = getStaticIcon(resources, icon);
            if(staticIcon == null) staticIcon = fetchFallbackIcon(resources);
            sStaticIconCache.put(icon, staticIcon);
        }
        sIconCache.put(identityHash, staticIcon);
        return staticIcon;
    }

    private static @NonNull Drawable fetchFallbackIcon(Resources resources) {
        Drawable fallbackIcon = sStaticIconCache.get(FALLBACK_ICON_NAME);
        if(fallbackIcon == null) {
            fallbackIcon = Objects.requireNonNull(getStaticIcon(resources, FALLBACK_ICON_NAME));
            sStaticIconCache.put(FALLBACK_ICON_NAME, fallbackIcon);
        }
        return fallbackIcon;
    }

    private static Drawable getStaticIcon(Resources resources, @NonNull String icon) {
        int staticIconResource = getStaticIconResource(icon);
        if(staticIconResource == -1) return null;
        return ResourcesCompat.getDrawable(resources, staticIconResource, null);
    }

    private static int getStaticIconResource(String icon) {
        Integer iconResource = sStaticIcons.get(icon);
        if(iconResource == null) return -1;
        return iconResource;
    }

    /**
     * Load a static icon, falling back to the default icon for unknown names.
     * @param resources the Resources object, used for creating drawables
     * @param name static icon name
     * @return the icon drawable
     */
    public static @NonNull Drawable getStaticIconDrawable(Resources resources, String name) {
        Drawable staticIcon = name != null ? getStaticIcon(resources, name) : null;
        return staticIcon != null ? staticIcon : copyIcon(resources, fetchFallbackIcon(resources));
    }

    /** @return the names of all static icons, in the order they should be offered to the user */
    public static Iterable<String> getStaticIconNames() {
        return sStaticIcons.keySet();
    }

    /**
     * @param name static icon name
     * @return the string resource describing the icon
     */
    public static @StringRes int getStaticIconLabel(String name) {
        Integer label = sStaticIconLabels.get(name);
        return label != null ? label : R.string.instance_icon_default;
    }

    /**
     * Check whether the icon under the specified name is a static icon available in the provider.
     * @param name static icon name to check
     * @return whether the icon is available or not
     */
    public static boolean hasStaticIcon(String name) {
        return sStaticIcons.containsKey(name);
    }
}
