package net.prismarin.shared.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class FacePixelsTest {

    static byte[] resource(String name) throws IOException {
        try (InputStream stream = FacePixelsTest.class.getClassLoader().getResourceAsStream("skins/" + name)) {
            if (stream == null) {
                throw new IOException("missing test skin " + name);
            }
            return stream.readAllBytes();
        }
    }

    private static int base(int x, int y) {
        return (16 * x + 5) << 16 | (16 * y + 7) << 8 | 200;
    }

    @Test
    void theFaceIsReadFromItsArea() throws IOException {
        List<Integer> pixels = FacePixels.read(resource("modern.png"));
        assertEquals(64, pixels.size());
        assertEquals(base(3, 2), pixels.get(2 * 8 + 3));
        assertEquals(base(7, 6), pixels.get(6 * 8 + 7));
        assertEquals(base(0, 7), pixels.get(7 * 8));
    }

    @Test
    void theHatCoversTheFaceWhereItIsSet() throws IOException {
        List<Integer> pixels = FacePixels.read(resource("modern.png"));
        assertEquals(0xFF0000, pixels.get(0));
        assertEquals(10 << 16 | 3 << 8 | 228, pixels.get(1));
        assertEquals(0x0AFA0A, pixels.get(63));
        assertEquals(base(2, 0), pixels.get(2));
    }

    @Test
    void anOpaqueHatOnAnOldSkinIsIgnoredButKeptOnANewOne() throws IOException {
        List<Integer> legacy = FacePixels.read(resource("legacy.png"));
        assertEquals(base(0, 0), legacy.get(0));
        assertEquals(base(5, 5), legacy.get(5 * 8 + 5));
        List<Integer> modern = FacePixels.read(resource("opaque-hat.png"));
        assertEquals(0x010203, modern.get(0));
        assertEquals(0x010203, modern.get(63));
    }

    @Test
    void blendingMixesByAlpha() {
        assertEquals(0x102030, FacePixels.blend(0xFF102030, 0x00FFFFFF));
        assertEquals(0xFFFFFF, FacePixels.blend(0xFF102030, 0xFFFFFFFF));
        assertEquals(0x808080, FacePixels.blend(0xFF000000, 0x80FFFFFF));
    }

    @Test
    void doubleSizeSkinsAreReadAndOddSizesRefused() {
        BufferedImage large = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        large.setRGB(16, 16, 0xFF112233);
        large.setRGB(17, 17, 0xFF445566);
        assertEquals(0x112233, FacePixels.extract(large).getFirst());
        assertThrows(IllegalArgumentException.class, () -> FacePixels.extract(
                new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB)));
        assertThrows(IllegalArgumentException.class, () -> FacePixels.read(new byte[]{1, 2, 3}));
    }
}
