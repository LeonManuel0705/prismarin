package net.prismarin.core.essentials;

import java.util.ArrayList;
import java.util.List;

public final class PingStats {

    private final List<Integer> samples = new ArrayList<>();

    public void add(int ping) {
        if (ping >= 0) {
            samples.add(ping);
        }
    }

    public int count() {
        return samples.size();
    }

    public int lowest() {
        return samples.stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    public int peak() {
        return samples.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public int average() {
        return (int) Math.round(samples.stream().mapToInt(Integer::intValue).average().orElse(0D));
    }

    public int spread() {
        return peak() - lowest();
    }
}
