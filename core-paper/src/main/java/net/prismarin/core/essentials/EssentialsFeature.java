package net.prismarin.core.essentials;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum EssentialsFeature {
    TPALL(List.of("tpall")),
    TPHERE(List.of("tphere")),
    HEAL(List.of("heal")),
    FEED(List.of("feed")),
    FLY(List.of("fly")),
    GAMEMODE(List.of("gm", "gmc", "gms", "gma", "gmsp")),
    SPEED(List.of("speed")),
    CLEARINVENTORY(List.of("clearinventory")),
    INVSEE(List.of("invsee")),
    ENDERCHEST(List.of("enderchest")),
    TIME(List.of("day", "night")),
    WEATHER(List.of("sun", "rain", "thunder")),
    PING(List.of("ping")),
    PINGTEST(List.of("pingtest")),
    LIST(List.of("list")),
    HAT(List.of("hat")),
    SKULL(List.of("skull"));

    public static final String PERMISSION_PREFIX = "prismarin.essentials.";

    private final List<String> commands;

    EssentialsFeature(List<String> commands) {
        this.commands = commands;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public List<String> commands() {
        return commands;
    }

    public String permission() {
        return PERMISSION_PREFIX + key();
    }

    public String permission(String suffix) {
        return permission() + "." + suffix;
    }

    public static Optional<EssentialsFeature> byCommand(String command) {
        String name = command.toLowerCase(Locale.ROOT);
        for (EssentialsFeature feature : values()) {
            if (feature.commands.contains(name)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }
}
