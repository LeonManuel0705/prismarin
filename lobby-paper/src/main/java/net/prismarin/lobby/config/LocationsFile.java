package net.prismarin.lobby.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class LocationsFile {

    private static final String RESOURCE_NAME = "locations.yml";

    private final Path file;
    private volatile SpawnPoint spawn;
    private volatile double voidTeleportY;

    private LocationsFile(Path file, SpawnPoint spawn, double voidTeleportY) {
        this.file = file;
        this.spawn = spawn;
        this.voidTeleportY = voidTeleportY;
    }

    public static LocationsFile load(Path file) {
        try {
            seedIfAbsent(file);
            ConfigurationNode root = YamlConfigurationLoader.builder().path(file).build().load();
            ConfigurationNode spawnNode = root.node("spawn");
            SpawnPoint spawn = new SpawnPoint(
                    spawnNode.node("world").getString("world"),
                    spawnNode.node("x").getDouble(0.5),
                    spawnNode.node("y").getDouble(4.0),
                    spawnNode.node("z").getDouble(0.5),
                    (float) spawnNode.node("yaw").getDouble(0.0),
                    (float) spawnNode.node("pitch").getDouble(0.0));
            double voidTeleportY = root.node("void-teleport-y").getDouble(0.0);
            return new LocationsFile(file, spawn, voidTeleportY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save() {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(file).build();
            ConfigurationNode root = loader.createNode();
            SpawnPoint current = spawn;
            root.node("spawn", "world").set(current.world());
            root.node("spawn", "x").set(current.x());
            root.node("spawn", "y").set(current.y());
            root.node("spawn", "z").set(current.z());
            root.node("spawn", "yaw").set((double) current.yaw());
            root.node("spawn", "pitch").set((double) current.pitch());
            root.node("void-teleport-y").set(voidTeleportY);
            loader.save(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public SpawnPoint spawn() {
        return spawn;
    }

    public void spawn(SpawnPoint spawn) {
        this.spawn = spawn;
    }

    public double voidTeleportY() {
        return voidTeleportY;
    }

    private static void seedIfAbsent(Path file) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream resource = LocationsFile.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (resource != null) {
                Files.copy(resource, file);
            }
        }
    }
}
