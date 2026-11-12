package net.prismarin.shared.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.skin.ChatFace;

public final class DefaultFaces {

    public static final String STEVE = "steve";
    public static final String ALEX = "alex";

    private static final int[] STEVE_PIXELS = {
            0x261A0A, 0x2F1F0F, 0x2F1F0F, 0x3F2A15, 0x2F1F0F, 0x2F1F0F, 0x2F1F0F, 0x261A0A,
            0x2F1F0F, 0x2F1F0F, 0x3F2A15, 0x2F1F0F, 0x2F1F0F, 0x2F1F0F, 0x3F2A15, 0x2F1F0F,
            0x2F1F0F, 0xB78A72, 0xB78A72, 0xB78A72, 0xB78A72, 0xB78A72, 0xB78A72, 0x2F1F0F,
            0xB78A72, 0xC69680, 0xB78A72, 0xB78A72, 0xB78A72, 0xB78A72, 0xC69680, 0xB78A72,
            0xB78A72, 0xFFFFFF, 0x523D89, 0xB78A72, 0xB78A72, 0x523D89, 0xFFFFFF, 0xB78A72,
            0xAA7D66, 0xB78A72, 0xB78A72, 0x87563E, 0x87563E, 0xB78A72, 0xB78A72, 0xAA7D66,
            0xAA7D66, 0xB78A72, 0x6A3F2F, 0x6A3F2F, 0x6A3F2F, 0x6A3F2F, 0xB78A72, 0xAA7D66,
            0x9C725C, 0xAA7D66, 0x6A3F2F, 0xAA7D66, 0xAA7D66, 0x6A3F2F, 0xAA7D66, 0x9C725C};

    private static final int[] ALEX_PIXELS = {
            0xE38E3A, 0xF0A956, 0xE38E3A, 0xE38E3A, 0xE38E3A, 0xE38E3A, 0xF0A956, 0xE38E3A,
            0xE38E3A, 0xE38E3A, 0xF0A956, 0xE38E3A, 0xE38E3A, 0xE38E3A, 0xE38E3A, 0xB86B26,
            0xE38E3A, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xE38E3A, 0xB86B26,
            0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xE38E3A,
            0xF3CCAD, 0xFFFFFF, 0x2F7A3B, 0xF3CCAD, 0xF3CCAD, 0x2F7A3B, 0xFFFFFF, 0xF3CCAD,
            0xE8B795, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xE8B795,
            0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xD98C7A, 0xD98C7A, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD,
            0xE8B795, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xF3CCAD, 0xE8B795};

    public static final ChatFace STEVE_FACE = face(STEVE_PIXELS, STEVE);
    public static final ChatFace ALEX_FACE = face(ALEX_PIXELS, ALEX);

    private DefaultFaces() {
        throw new AssertionError();
    }

    public static boolean slim(UUID owner) {
        return owner != null && (owner.hashCode() & 1) == 1;
    }

    public static ChatFace forOwner(UUID owner) {
        return slim(owner) ? ALEX_FACE : STEVE_FACE;
    }

    public static ChatFace forModel(boolean slim) {
        return slim ? ALEX_FACE : STEVE_FACE;
    }

    private static ChatFace face(int[] values, String source) {
        List<Integer> pixels = new ArrayList<>(values.length);
        for (int value : values) {
            pixels.add(value);
        }
        return new ChatFace(pixels, source, true);
    }
}
