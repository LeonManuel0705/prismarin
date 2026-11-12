package net.prismarin.core.joinme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.joinme.ArrivalPlan;
import net.prismarin.shared.skin.SkinFaceService;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record BackendJoinMeSettings(boolean sameWorld, ArrivalPlan.Position position, int delayTicks,
                                    boolean sendBackWhenRefused, boolean arrivalMessage,
                                    JoinMeSubCommands subCommands, boolean navigatorEntry,
                                    SkinFaceService.Settings faceCache, List<String> problems) {

    public static final String FILE_NAME = "joinme.yml";

    private static final List<String> HEADER = List.of(
            "JoinMe on this game server. The rules for /joinme (cooldowns, costs, lifetime, audience, groups)",
            "are in joinme.yml on the proxy, the texts in the messages files under joinme.",
            "",
            "A player who clicks a JoinMe arrives here. A game plugin that registered a JoinMe handler decides",
            "what happens next. Without one, or when the handler hands the player back, the fallback below",
            "puts him into the creator's world.");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("fallback", List.of("What happens without a game plugin handler.")),
            Map.entry("fallback.same-world", List.of(
                    "Puts the player into the world of the creator, for servers with one arena per world.",
                    "false leaves him where the server puts every joining player.")),
            Map.entry("fallback.position", List.of(
                    "spawn: the spawn of that world. creator: right next to the creator.")),
            Map.entry("arrival", List.of("Arriving players.")),
            Map.entry("arrival.delay-ticks", List.of(
                    "Ticks to wait after the join before the player is moved, so join plugins finish first.")),
            Map.entry("arrival.send-back-when-refused", List.of(
                    "Sends the player back to the server he came from when a handler refuses him.")),
            Map.entry("arrival.message", List.of("Tells the player whom he joined (joinme.arrived).")),
            Map.entry("sub-commands", List.of(
                    "false hides a part of /joinme everywhere: help, tab completion and menus.",
                    "preview sends the JoinMe message only to the player who types /joinme preview, on every",
                    "server and without cooldown or cost, so he can see how it looks. It needs prismarin.joinme.")),
            Map.entry("lobby.navigator-entry", List.of(
                    "With PrismarinLobby: a JoinMe entry in the navigator that lists the open JoinMes as heads.")),
            Map.entry("face", List.of(
                    "Skin faces for prismarin.skinFaces() on this server: 8 x 8 chat pixels of a player's face.",
                    "Skins come only from the public URL on textures.minecraft.net.")),
            Map.entry("face.cache-size", List.of("Faces kept in memory, by texture.")),
            Map.entry("face.disk-cache", List.of("Also keeps computed faces in the folder skin-faces.")),
            Map.entry("face.download-timeout-millis", List.of("Longest wait for one skin download.")));

    public BackendJoinMeSettings {
        position = position == null ? ArrivalPlan.Position.SPAWN : position;
        subCommands = subCommands == null ? JoinMeSubCommands.ALL : subCommands;
        faceCache = faceCache == null ? SkinFaceService.Settings.DEFAULT : faceCache;
        problems = List.copyOf(problems);
    }

    public static BackendJoinMeSettings load(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root;
            try {
                root = Files.exists(file) ? loader.load() : loader.createNode();
            } catch (ConfigurateException e) {
                return read(filled(), List.of(FILE_NAME + " could not be read, using the defaults: "
                        + e.getMessage()));
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, List.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BackendJoinMeSettings defaults() {
        return read(filled(), List.of());
    }

    static BackendJoinMeSettings read(ConfigurationNode root, List<String> initial) {
        List<String> problems = new ArrayList<>(initial);
        String positionText = root.node("fallback", "position").getString("spawn");
        ArrivalPlan.Position position = ArrivalPlan.Position.byId(positionText).orElseGet(() -> {
            problems.add("fallback.position '" + positionText + "' is neither spawn nor creator, using spawn");
            return ArrivalPlan.Position.SPAWN;
        });
        Set<String> off = new LinkedHashSet<>();
        for (String key : JoinMeSubCommands.SWITCHES) {
            if (!root.node("sub-commands", key).getBoolean(true)) {
                off.add(key);
            }
        }
        root.node("sub-commands").childrenMap().keySet().forEach(key -> {
            if (!JoinMeSubCommands.SWITCHES.contains(key.toString())) {
                problems.add("sub-commands." + key + " is unknown, known are " + JoinMeSubCommands.SWITCHES);
            }
        });
        ConfigurationNode arrival = root.node("arrival");
        return new BackendJoinMeSettings(root.node("fallback", "same-world").getBoolean(true), position,
                Math.clamp(arrival.node("delay-ticks").getInt(10), 0, 200),
                arrival.node("send-back-when-refused").getBoolean(true), arrival.node("message").getBoolean(true),
                new JoinMeSubCommands(off), root.node("lobby", "navigator-entry").getBoolean(true),
                new SkinFaceService.Settings(root.node("face", "cache-size").getInt(512),
                        root.node("face", "disk-cache").getBoolean(true),
                        root.node("face", "download-timeout-millis").getLong(3_000L)), problems);
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        changed |= setIfAbsent(root.node("fallback", "same-world"), true);
        changed |= setIfAbsent(root.node("fallback", "position"), "spawn");
        changed |= setIfAbsent(root.node("arrival", "delay-ticks"), 10);
        changed |= setIfAbsent(root.node("arrival", "send-back-when-refused"), true);
        changed |= setIfAbsent(root.node("arrival", "message"), true);
        for (String key : JoinMeSubCommands.SWITCHES) {
            changed |= setIfAbsent(root.node("sub-commands", key), true);
        }
        changed |= setIfAbsent(root.node("lobby", "navigator-entry"), true);
        changed |= setIfAbsent(root.node("face", "cache-size"), 512);
        changed |= setIfAbsent(root.node("face", "disk-cache"), true);
        changed |= setIfAbsent(root.node("face", "download-timeout-millis"), 3_000);
        return changed;
    }

    private static CommentedConfigurationNode filled() {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            fillDefaults(root);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
