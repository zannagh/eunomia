package de.zannagh.eunomia.render;

/**
 * Built-in {@link ColorTransformer}. Uses straight ARGB arithmetic via {@link ColorConverter} so the
 * bit-twiddling is centralized and version-aware in one place.
 */
public final class DefaultColorTransformer implements ColorTransformer {

    private static final DefaultColorTransformer INSTANCE = new DefaultColorTransformer();

    private final ColorConverter colorConverter = new ColorConverter();

    public static DefaultColorTransformer getInstance() {
        return INSTANCE;
    }

    private DefaultColorTransformer() {
    }

    @Override
    public int applyTransparency(int color, float transparency) {
        return colorConverter.applyTransparency(color, transparency);
    }

    @Override
    public int scaleAlpha(int color, float transparency) {
        return colorConverter.scaleAlpha(color, transparency);
    }

    @Override
    public int whiteWithTransparency(float transparency) {
        return colorConverter.whiteWithTransparency(transparency);
    }
}
