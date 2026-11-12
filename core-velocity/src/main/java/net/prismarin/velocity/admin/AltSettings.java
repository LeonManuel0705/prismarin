package net.prismarin.velocity.admin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.moderation.Durations;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record AltSettings(int maxAddresses, int sharedLimit, long retentionMillis, boolean alertStaff,
                          boolean enforceBans, List<String> problems) {

    public static final String FILE_NAME = "alts.yml";

    static final int DEFAULT_MAX_ADDRESSES = 10;
    static final int DEFAULT_SHARED_LIMIT = 10;
    static final String DEFAULT_RETENTION = "90d";

    private static final List<String> HEADER = List.of(
            "Alt account detection on the proxy. Every login stores a salted SHA-256 hash of the address",
            "(IPv6 as its /64 network), never the address itself. The salt is the one IP bans use.",
            "Accounts that used the same address are listed with /alts <player>, in /check and in /pinfo.",
            "prismarin.staff.alts allows /alts and shows the alerts, prismarin.alts.bypass skips the checks below.");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("max-addresses-per-player", List.of("How many different addresses are kept per player,",
                    "the oldest one goes first.")),
            Map.entry("shared-address-limit", List.of("An address used by more accounts than this is treated as shared,",
                    "for example a school, a mobile network or a proxy in front of the network, and links nobody.")),
            Map.entry("retention", List.of("How long an address is remembered after its last login, for example 30d or 1y.",
                    "Older entries are removed on start and once a day.")),
            Map.entry("alert-staff", List.of("Tell staff with prismarin.staff.alts when an account joins that shares an",
                    "address with a banned account.")),
            Map.entry("enforce-bans", List.of("true also keeps such accounts out with the ban screen of the banned account",
                    "and removes online alts when an account is banned. Careful with shared addresses.")));

    public AltSettings {
        problems = List.copyOf(problems);
    }

    public static AltSettings defaults() {
        return new AltSettings(DEFAULT_MAX_ADDRESSES, DEFAULT_SHARED_LIMIT,
                Durations.parse(DEFAULT_RETENTION).orElseThrow().toMillis(), true, false, List.of());
    }

    public static AltSettings load(Path file) {
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
                AltSettings defaults = defaults();
                return new AltSettings(defaults.maxAddresses(), defaults.sharedLimit(), defaults.retentionMillis(),
                        defaults.alertStaff(), defaults.enforceBans(),
                        List.of(FILE_NAME + " could not be read, using the defaults: " + e.getMessage()));
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static AltSettings read(ConfigurationNode root) {
        List<String> problems = new ArrayList<>();
        String retentionText = root.node("retention").getString(DEFAULT_RETENTION);
        long retention = Durations.parse(retentionText).map(Duration::toMillis).orElseGet(() -> {
            problems.add("retention: '" + retentionText + "' is no duration such as 30d, using " + DEFAULT_RETENTION);
            return Durations.parse(DEFAULT_RETENTION).orElseThrow().toMillis();
        });
        return new AltSettings(Math.clamp(root.node("max-addresses-per-player").getInt(DEFAULT_MAX_ADDRESSES), 1, 100),
                Math.clamp(root.node("shared-address-limit").getInt(DEFAULT_SHARED_LIMIT), 2, 200), retention,
                root.node("alert-staff").getBoolean(true), root.node("enforce-bans").getBoolean(false), problems);
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = setIfAbsent(root.node("max-addresses-per-player"), DEFAULT_MAX_ADDRESSES);
        changed |= setIfAbsent(root.node("shared-address-limit"), DEFAULT_SHARED_LIMIT);
        changed |= setIfAbsent(root.node("retention"), DEFAULT_RETENTION);
        changed |= setIfAbsent(root.node("alert-staff"), true);
        changed |= setIfAbsent(root.node("enforce-bans"), false);
        return changed;
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
