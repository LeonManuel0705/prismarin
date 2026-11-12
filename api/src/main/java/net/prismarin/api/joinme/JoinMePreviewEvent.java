package net.prismarin.api.joinme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public final class JoinMePreviewEvent implements PrismarinEvent {

    private final JoinMeSpot spot;
    private final Map<String, String> placeholders;
    private final Map<Integer, String> lines;

    public JoinMePreviewEvent(JoinMeSpot spot, Map<String, String> placeholders, Map<Integer, String> lines) {
        this.spot = Objects.requireNonNull(spot, "spot");
        this.placeholders = Collections.synchronizedMap(new LinkedHashMap<>(placeholders));
        this.lines = Collections.synchronizedMap(new TreeMap<>(lines));
    }

    public UUID creator() {
        return spot.creator();
    }

    public JoinMeSpot spot() {
        return spot;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }

    public void placeholder(String key, String value) {
        placeholders.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    public Map<Integer, String> lines() {
        return lines;
    }

    public void line(int row, String text) {
        if (row < 1) {
            throw new IllegalArgumentException("rows start at 1");
        }
        lines.put(row, Objects.requireNonNull(text, "text"));
    }
}
