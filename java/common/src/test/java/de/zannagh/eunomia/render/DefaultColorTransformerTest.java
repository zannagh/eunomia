package de.zannagh.eunomia.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultColorTransformerTest {

    private final DefaultColorTransformer transformer = DefaultColorTransformer.getInstance();

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    @Test
    void getInstanceReturnsSameSingleton() {
        assertThat(DefaultColorTransformer.getInstance())
                .isNotNull()
                .isSameAs(DefaultColorTransformer.getInstance());
    }

    @Test
    void isAColorTransformer() {
        assertThat((ColorTransformer) transformer).isInstanceOf(ColorTransformer.class);
    }

    // --- applyTransparency: replaces alpha, preserves RGB ---------------------------------------

    @Test
    void applyTransparencyFullyOpaqueSetsMaxAlphaAndKeepsRgb() {
        int color = 0x12345678; // alpha 0x12, rgb 0x345678
        int result = transformer.applyTransparency(color, 1.0f);

        assertThat(alphaOf(result)).isEqualTo(255);
        assertThat(rgbOf(result)).isEqualTo(0x345678);
    }

    @Test
    void applyTransparencyFullyTransparentZeroesAlphaAndKeepsRgb() {
        int color = 0xFFABCDEF;
        int result = transformer.applyTransparency(color, 0.0f);

        assertThat(alphaOf(result)).isEqualTo(0);
        assertThat(rgbOf(result)).isEqualTo(0xABCDEF);
    }

    @Test
    void applyTransparencyOverwritesExistingAlphaRatherThanScaling() {
        // Existing alpha 0x00 must not suppress the newly computed opacity.
        int color = 0x00FF0000; // transparent red
        int result = transformer.applyTransparency(color, 1.0f);

        assertThat(alphaOf(result)).isEqualTo(255);
        assertThat(rgbOf(result)).isEqualTo(0xFF0000);
    }

    @Test
    void applyTransparencyRoundsHalfUpToNearestAlpha() {
        // 0.5 * 255 = 127.5 -> Math.round -> 128
        assertThat(alphaOf(transformer.applyTransparency(0x00000000, 0.5f))).isEqualTo(128);
        // 0.25 * 255 = 63.75 -> 64
        assertThat(alphaOf(transformer.applyTransparency(0x00000000, 0.25f))).isEqualTo(64);
        // 0.75 * 255 = 191.25 -> 191
        assertThat(alphaOf(transformer.applyTransparency(0x00000000, 0.75f))).isEqualTo(191);
    }

    @Test
    void applyTransparencyPreservesRgbForBlackAndWhite() {
        assertThat(rgbOf(transformer.applyTransparency(0xFF000000, 0.5f))).isEqualTo(0x000000);
        assertThat(rgbOf(transformer.applyTransparency(0xFFFFFFFF, 0.5f))).isEqualTo(0xFFFFFF);
    }

    // --- whiteWithTransparency: always white RGB, alpha from transparency -----------------------

    @Test
    void whiteWithTransparencyFullOpacityIsOpaqueWhite() {
        assertThat(transformer.whiteWithTransparency(1.0f)).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void whiteWithTransparencyZeroIsTransparentWhite() {
        assertThat(transformer.whiteWithTransparency(0.0f)).isEqualTo(0x00FFFFFF);
    }

    @Test
    void whiteWithTransparencyHalfIsHalfAlphaWhite() {
        int result = transformer.whiteWithTransparency(0.5f);
        assertThat(rgbOf(result)).isEqualTo(0xFFFFFF);
        assertThat(alphaOf(result)).isEqualTo(128);
    }

    @Test
    void whiteWithTransparencyAlphaMatchesApplyTransparencyAlpha() {
        // Both derive alpha from Math.round(transparency * 255), so they must agree.
        for (float t : new float[] {0.0f, 0.1f, 0.33f, 0.5f, 0.9f, 1.0f}) {
            assertThat(alphaOf(transformer.whiteWithTransparency(t)))
                    .as("alpha for t=%s", t)
                    .isEqualTo(alphaOf(transformer.applyTransparency(0xFFFFFF, t)));
        }
    }

    // --- scaleAlpha: multiplies existing alpha, preserves RGB (pure bit math) --------------------

    @Test
    void scaleAlphaByOneIsIdentity() {
        assertThat(transformer.scaleAlpha(0xC0123456, 1.0f)).isEqualTo(0xC0123456);
    }

    @Test
    void scaleAlphaByZeroClearsAlphaButKeepsRgb() {
        assertThat(transformer.scaleAlpha(0xFFAABBCC, 0.0f)).isEqualTo(0x00AABBCC);
    }

    @Test
    void scaleAlphaHalvesFullAlpha() {
        // origAlpha 255, 0.5 * 255 = 127.5 -> round -> 128
        assertThat(transformer.scaleAlpha(0xFF000000, 0.5f)).isEqualTo(0x80000000);
    }

    @Test
    void scaleAlphaHalvesPartialAlphaAndKeepsWhiteRgb() {
        // origAlpha 128, 0.5 * 128 = 64
        assertThat(transformer.scaleAlpha(0x80FFFFFF, 0.5f)).isEqualTo(0x40FFFFFF);
    }

    @Test
    void scaleAlphaOnZeroAlphaStaysZeroRegardlessOfFactor() {
        // origAlpha 0 -> any multiplier yields 0.
        assertThat(transformer.scaleAlpha(0x00ABCDEF, 1.0f)).isEqualTo(0x00ABCDEF);
        assertThat(transformer.scaleAlpha(0x00ABCDEF, 0.5f)).isEqualTo(0x00ABCDEF);
    }

    @Test
    void scaleAlphaRoundsHalfUp() {
        // origAlpha 255, 0.1 * 255 = 25.5 -> round -> 26
        assertThat(alphaOf(transformer.scaleAlpha(0xFF000000, 0.1f))).isEqualTo(26);
    }

    @Test
    void scaleAlphaAlwaysPreservesRgbChannels() {
        int color = 0x99123456;
        for (float t : new float[] {0.0f, 0.25f, 0.5f, 0.75f, 1.0f}) {
            assertThat(rgbOf(transformer.scaleAlpha(color, t)))
                    .as("rgb for t=%s", t)
                    .isEqualTo(0x123456);
        }
    }

    @Test
    void scaleAlphaDiffersFromApplyTransparencyWhenExistingAlphaIsPartial() {
        // scaleAlpha composes on top of the existing 0x80 alpha; applyTransparency overwrites it.
        int color = 0x80FF0000;
        assertThat(alphaOf(transformer.scaleAlpha(color, 0.5f))).isEqualTo(64);
        assertThat(alphaOf(transformer.applyTransparency(color, 0.5f))).isEqualTo(128);
    }
}
