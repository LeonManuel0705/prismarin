package net.prismarin.lobby.action;

import java.util.Locale;
import java.util.regex.Pattern;

public record SoundSpec(String key, float volume, float pitch) {

    private static final Pattern KEY = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final float MAX_PITCH = 2.0f;

    public static SoundSpec parse(String raw) {
        String[] parts = raw.strip().split("\\s+");
        String key = parts[0].toLowerCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + parts[0] + "' is no valid sound key");
        }
        if (parts.length > 3) {
            throw new IllegalArgumentException("a sound takes at most a key, a volume and a pitch");
        }
        float volume = parts.length > 1 ? number(parts[1], "volume") : 1.0f;
        float pitch = parts.length > 2 ? number(parts[2], "pitch") : 1.0f;
        if (volume < 0.0f || pitch < 0.0f || pitch > MAX_PITCH) {
            throw new IllegalArgumentException("volume must be positive and pitch between 0 and 2");
        }
        return new SoundSpec(key, volume, pitch);
    }

    private static float number(String value, String label) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("sound " + label + " '" + value + "' is no number");
        }
    }
}
