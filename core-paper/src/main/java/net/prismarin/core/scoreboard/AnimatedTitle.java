package net.prismarin.core.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.brand.Brand;

public final class AnimatedTitle {

    private static final int DEFAULT_FRAME_COUNT = 12;

    private final List<Component> frames;
    private final AtomicInteger cursor = new AtomicInteger();

    public AnimatedTitle(List<Component> frames) {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        this.frames = List.copyOf(frames);
    }

    public static AnimatedTitle prismarin(MiniMessage miniMessage) {
        return brandGradient(miniMessage, Brand.NAME, DEFAULT_FRAME_COUNT);
    }

    public static AnimatedTitle brandGradient(MiniMessage miniMessage, String text, int frameCount) {
        if (frameCount < 1) {
            throw new IllegalArgumentException("frameCount must be at least 1");
        }
        List<Component> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            double phase = (double) i / frameCount;
            String frame = "<gradient:" + Brand.PRIMARY + ":" + Brand.ACCENT + ":"
                    + String.format(Locale.ROOT, "%.4f", phase)
                    + "><b>" + text + "</b></gradient>";
            frames.add(miniMessage.deserialize(frame));
        }
        return new AnimatedTitle(frames);
    }

    public Component next() {
        return frames.get(Math.floorMod(cursor.getAndIncrement(), frames.size()));
    }

    public Component frame(int index) {
        return frames.get(Math.floorMod(index, frames.size()));
    }

    public int frameCount() {
        return frames.size();
    }
}
