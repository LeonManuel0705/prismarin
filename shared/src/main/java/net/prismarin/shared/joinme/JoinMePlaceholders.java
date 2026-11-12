package net.prismarin.shared.joinme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.joinme.JoinMe;

public final class JoinMePlaceholders {

    public static final String PLAYERS = "players";
    public static final String MAX_PLAYERS = "max_players";
    public static final String WORLD_PLAYERS = "world_players";
    public static final Set<String> RICH = Set.of("group", "server_display", "state");
    private static final Set<String> FIXED = Set.of("creator", "id", "server", "group_id", "world", "joins",
            "max_joins", "joins_left", "time", "seconds");

    private JoinMePlaceholders() {
        throw new AssertionError();
    }

    public static Map<String, String> values(JoinMe joinMe, boolean revealed, long now) {
        Map<String, String> values = new LinkedHashMap<>();
        joinMe.placeholders().forEach((key, value) -> {
            if (!FIXED.contains(key) && !RICH.contains(key)) {
                values.put(key, value);
            }
        });
        values.put("creator", joinMe.name(revealed));
        values.put("id", joinMe.id());
        values.put("server", joinMe.server());
        values.put("group_id", joinMe.group());
        values.put("world", joinMe.world());
        values.put("joins", Integer.toString(joinMe.joins()));
        values.put("max_joins", joinMe.maxJoins() > 0 ? Integer.toString(joinMe.maxJoins()) : "-");
        values.put("joins_left", joinMe.joinsLeft() < 0 ? "-" : Integer.toString(joinMe.joinsLeft()));
        long remaining = joinMe.remainingMillis(now);
        values.put("time", clock(remaining));
        values.put("seconds", Long.toString((remaining + 999L) / 1000L));
        values.putIfAbsent(PLAYERS, "?");
        values.putIfAbsent(MAX_PLAYERS, "?");
        values.putIfAbsent(WORLD_PLAYERS, values.get(PLAYERS));
        values.putIfAbsent("map", joinMe.world().isEmpty() ? joinMe.server() : joinMe.world());
        values.putIfAbsent("mode", "");
        return values;
    }

    public static Map<String, String> withStatus(Map<String, String> values, int players, int maxPlayers) {
        Map<String, String> updated = new LinkedHashMap<>(values);
        if (players >= 0) {
            updated.put(PLAYERS, Integer.toString(players));
        }
        if (maxPlayers > 0) {
            updated.put(MAX_PLAYERS, Integer.toString(maxPlayers));
        }
        return updated;
    }

    public static TagResolver resolver(Map<String, String> values, Map<String, Component> rich) {
        List<TagResolver> resolvers = new ArrayList<>();
        values.forEach((key, value) -> {
            if (!rich.containsKey(key)) {
                resolvers.add(Placeholder.unparsed(key, value));
            }
        });
        rich.forEach((key, value) -> resolvers.add(Placeholder.component(key, value)));
        return TagResolver.resolver(resolvers);
    }

    public static String clock(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long rest = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
