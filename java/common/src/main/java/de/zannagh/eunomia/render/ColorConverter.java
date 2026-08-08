package de.zannagh.eunomia.render;

//? if >= 1.21.4
import net.minecraft.util.ARGB;

/**
 * A utility class for color conversions.
 */
public final class ColorConverter {

    /**
     * Converts the given color with the specified alpha value.
     * @param originalColor The original color.
     * @param alpha The alpha value.
     * @return The color with the specified alpha value.
     */
    public static int withAlpha(int originalColor, int alpha) {
        //? if >= 1.21.4
        return ARGB.color(alpha, ARGB.red(originalColor), ARGB.green(originalColor), ARGB.blue(originalColor));
        //? if < 1.21.4 {
        /*int red = (originalColor >> 16) & 0xFF;
        int green = (originalColor >> 8) & 0xFF;
        int blue = originalColor & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
        *///?}
    }

    /**
     * Creates a white color with the specified alpha value.
     * @param alpha The alpha value.
     * @return The white color with the specified alpha value.
     */
    public static int whiteWithAlpha(int alpha) {
        //? if >= 1.21.4
        return ARGB.color(alpha, 255, 255, 255);
        //? if < 1.21.4
        //return (alpha << 24) | (255 << 16) | (255 << 8) | 255;
    }

    public int applyTransparency(int color, float transparency) {
        int alpha = Math.round(transparency * 255);
        return withAlpha(color, alpha);
    }

    public int scaleAlpha(int color, float transparency) {
        int origAlpha = (color >> 24) & 0xFF;
        int newAlpha = Math.round(transparency * origAlpha);
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    public int whiteWithTransparency(float transparency) {
        int alpha = Math.round(transparency * 255);
        return whiteWithAlpha(alpha);
    }
}
