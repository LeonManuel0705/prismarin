package net.prismarin.shared.skin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.prismarin.api.skin.ChatFace;

public final class FacePixels {

    public static final int FACE_X = 8;
    public static final int FACE_Y = 8;
    public static final int HAT_X = 40;
    public static final int HAT_Y = 8;
    private static final int SKIN_WIDTH = 64;

    private FacePixels() {
        throw new AssertionError();
    }

    public static List<Integer> read(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalArgumentException("the skin is no image");
            }
            return extract(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Integer> extract(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < SKIN_WIDTH || width % SKIN_WIDTH != 0 || height != width && height != width / 2) {
            throw new IllegalArgumentException("a skin is 64x64 or 64x32 (or a multiple), got " + width + "x"
                    + height);
        }
        int scale = width / SKIN_WIDTH;
        boolean legacy = height == width / 2;
        boolean hat = !legacy || !opaqueHat(image, scale);
        List<Integer> pixels = new ArrayList<>(ChatFace.SIZE * ChatFace.SIZE);
        for (int y = 0; y < ChatFace.SIZE; y++) {
            for (int x = 0; x < ChatFace.SIZE; x++) {
                int base = image.getRGB((FACE_X + x) * scale, (FACE_Y + y) * scale);
                int layer = hat ? image.getRGB((HAT_X + x) * scale, (HAT_Y + y) * scale) : 0;
                pixels.add(blend(base, layer));
            }
        }
        return List.copyOf(pixels);
    }

    public static int blend(int base, int layer) {
        int alpha = layer >>> 24;
        if (alpha == 0) {
            return base & 0xFFFFFF;
        }
        if (alpha == 255) {
            return layer & 0xFFFFFF;
        }
        double share = alpha / 255.0D;
        int red = mix(base >> 16 & 0xFF, layer >> 16 & 0xFF, share);
        int green = mix(base >> 8 & 0xFF, layer >> 8 & 0xFF, share);
        int blue = mix(base & 0xFF, layer & 0xFF, share);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int base, int layer, double share) {
        return (int) Math.round(layer * share + base * (1.0D - share));
    }

    private static boolean opaqueHat(BufferedImage image, int scale) {
        for (int y = 0; y < ChatFace.SIZE; y++) {
            for (int x = 0; x < ChatFace.SIZE; x++) {
                if (image.getRGB((HAT_X + x) * scale, (HAT_Y + y) * scale) >>> 24 != 255) {
                    return false;
                }
            }
        }
        return true;
    }
}
