package net.prismarin.lobby.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.prismarin.shared.config.YamlComments;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class LocationsFile {

    static final SpawnPoint SEEDED_SPAWN = new SpawnPoint("world", 0.5, 4.0, 0.5, 0.0f, 0.0f);
    static final double SEEDED_VOID_HEIGHT = 0.0;
    private static final List<String> HEADER = List.of(
            "Lobby spawns and void teleport heights per world.",
            "Written by /setspawn and /setvoid, you rarely need to edit this file by hand.");

    private final Path file;
    private volatile Map<String, SpawnPoint> spawns;
    private volatile Map<String, Double> voidHeights;

    private LocationsFile(Path file, Map<String, SpawnPoint> spawns, Map<String, Double> voidHeights) {
        this.file = file;
        this.spawns = Collections.unmodifiableMap(new LinkedHashMap<>(spawns));
        this.voidHeights = Collections.unmodifiableMap(new LinkedHashMap<>(voidHeights));
    }

    public static LocationsFile load(Path file) {
        if (Files.notExists(file)) {
            return new LocationsFile(file, Map.of(), Map.of());
        }
        try {
            ConfigurationNode root = loader(file).load();
            Map<String, SpawnPoint> spawns = new LinkedHashMap<>();
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("spawns").childrenMap().entrySet()) {
                String world = entry.getKey().toString();
                spawns.put(world, read(world, entry.getValue()));
            }
            Map<String, Double> voidHeights = new LinkedHashMap<>();
            for (Map.Entry<Object, ? extends ConfigurationNode> entry
                    : root.node("void-heights").childrenMap().entrySet()) {
                Object value = entry.getValue().raw();
                if (value instanceof Number number) {
                    voidHeights.put(entry.getKey().toString(), number.doubleValue());
                }
            }
            ConfigurationNode legacy = root.node("spawn");
            boolean migrate = !legacy.virtual() || !root.node("void-teleport-y").virtual();
            if (!legacy.virtual()) {
                SpawnPoint old = read(legacy.node("world").getString("world"), legacy);
                if (!old.equals(SEEDED_SPAWN)) {
                    spawns.putIfAbsent(old.world(), old);
                    ConfigurationNode legacyVoid = root.node("void-teleport-y");
                    double height = legacyVoid.getDouble(SEEDED_VOID_HEIGHT);
                    if (!legacyVoid.virtual() && height != SEEDED_VOID_HEIGHT) {
                        voidHeights.putIfAbsent(old.world(), height);
                    }
                }
            }
            LocationsFile locations = new LocationsFile(file, spawns, voidHeights);
            if (migrate) {
                locations.save();
            } else {
                YamlComments.apply(file, HEADER, Map.of());
            }
            return locations;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loader(file);
            ConfigurationNode root = loader.createNode();
            for (Map.Entry<String, SpawnPoint> entry : spawns.entrySet()) {
                write(root.node("spawns", entry.getKey()), entry.getValue());
            }
            if (spawns.isEmpty()) {
                root.node("spawns").set(Map.of());
            }
            for (Map.Entry<String, Double> entry : voidHeights.entrySet()) {
                root.node("void-heights", entry.getKey()).set(entry.getValue());
            }
            if (voidHeights.isEmpty()) {
                root.node("void-heights").set(Map.of());
            }
            loader.save(root);
            YamlComments.apply(file, HEADER, Map.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, SpawnPoint> spawns() {
        return spawns;
    }

    public Optional<SpawnPoint> spawn(String world) {
        return Optional.ofNullable(spawns.get(world));
    }

    public synchronized void spawn(SpawnPoint point) {
        Map<String, SpawnPoint> updated = new LinkedHashMap<>(spawns);
        updated.put(point.world(), point);
        spawns = Collections.unmodifiableMap(updated);
    }

    public synchronized boolean removeSpawn(String world) {
        if (!spawns.containsKey(world)) {
            return false;
        }
        Map<String, SpawnPoint> updated = new LinkedHashMap<>(spawns);
        updated.remove(world);
        spawns = Collections.unmodifiableMap(updated);
        return true;
    }

    public Map<String, Double> voidHeights() {
        return voidHeights;
    }

    public OptionalDouble voidHeight(String world) {
        Double height = voidHeights.get(world);
        return height == null ? OptionalDouble.empty() : OptionalDouble.of(height);
    }

    public synchronized void voidHeight(String world, OptionalDouble height) {
        Map<String, Double> updated = new LinkedHashMap<>(voidHeights);
        if (height.isPresent()) {
            updated.put(world, height.getAsDouble());
        } else {
            updated.remove(world);
        }
        voidHeights = Collections.unmodifiableMap(updated);
    }

    public Optional<String> firstSpawnWorld() {
        return spawns.keySet().stream().findFirst();
    }

    private static SpawnPoint read(String world, ConfigurationNode node) {
        return new SpawnPoint(world,
                node.node("x").getDouble(0.5),
                node.node("y").getDouble(64.0),
                node.node("z").getDouble(0.5),
                (float) node.node("yaw").getDouble(0.0),
                (float) node.node("pitch").getDouble(0.0));
    }

    private static void write(ConfigurationNode node, SpawnPoint point) throws SerializationException {
        node.node("x").set(point.x());
        node.node("y").set(point.y());
        node.node("z").set(point.z());
        node.node("yaw").set((double) point.yaw());
        node.node("pitch").set((double) point.pitch());
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
