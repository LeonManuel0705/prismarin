package net.prismarin.core.config;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class RankDisplayHolder implements Supplier<RankDisplaySettings> {

    private final Path file;
    private final AtomicReference<RankDisplaySettings> current;

    public RankDisplayHolder(Path file, RankDisplaySettings initial) {
        this.file = file;
        this.current = new AtomicReference<>(initial);
    }

    @Override
    public RankDisplaySettings get() {
        return current.get();
    }

    public void reload() {
        current.set(RankDisplaySettings.load(file));
    }
}
