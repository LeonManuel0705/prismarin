package net.prismarin.api.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

public record ChatFace(List<Integer> pixels, String source, boolean fallback) {

    public static final int SIZE = 8;
    public static final String SYMBOL = "\u2588";

    public ChatFace {
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.size() != SIZE * SIZE) {
            throw new IllegalArgumentException("a face has " + SIZE * SIZE + " pixels, got " + pixels.size());
        }
        List<Integer> masked = new ArrayList<>(pixels.size());
        for (Integer pixel : pixels) {
            masked.add(pixel == null ? 0 : pixel & 0xFFFFFF);
        }
        pixels = List.copyOf(masked);
        source = source == null ? "" : source;
    }

    public int rgb(int x, int y) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            throw new IndexOutOfBoundsException("pixel " + x + "," + y + " is outside of the face");
        }
        return pixels.get(y * SIZE + x);
    }

    public TextColor color(int x, int y) {
        return TextColor.color(rgb(x, y));
    }

    public Component line(int row, String symbol) {
        String glyph = symbol == null || symbol.isEmpty() ? SYMBOL : symbol;
        TextComponent.Builder line = Component.text();
        int x = 0;
        while (x < SIZE) {
            int rgb = rgb(x, row);
            int end = x;
            while (end + 1 < SIZE && rgb(end + 1, row) == rgb) {
                end++;
            }
            line.append(Component.text(glyph.repeat(end - x + 1), TextColor.color(rgb)));
            x = end + 1;
        }
        return line.build();
    }

    public List<Component> lines(String symbol) {
        List<Component> lines = new ArrayList<>(SIZE);
        for (int row = 0; row < SIZE; row++) {
            lines.add(line(row, symbol));
        }
        return List.copyOf(lines);
    }

    public List<Component> lines() {
        return lines(SYMBOL);
    }
}
