package net.prismarin.shared.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerWire {

    public static final String QUERY = "player-query";
    public static final String ANSWER = "player-answer";
    public static final String STATE = "player-state";
    public static final int MAX_PLAYERS = 100;
    public static final int MAX_NAMES = 10;

    private PlayerWire() {
        throw new AssertionError();
    }

    public record Live(UUID uuid, String name, String nick, String server, String world, String locale,
                       boolean vanished) {

        public Live {
            name = text(name);
            nick = text(nick);
            server = text(server);
            world = text(world);
            locale = text(locale);
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", uuid.toString());
            object.addProperty("name", name);
            object.addProperty("nick", nick);
            object.addProperty("server", server);
            object.addProperty("world", world);
            object.addProperty("locale", locale);
            object.addProperty("vanished", vanished);
            return object;
        }

        public static Optional<Live> fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            return parseUuid(object.get("uuid")).map(uuid -> new Live(uuid, string(object, "name"),
                    string(object, "nick"), string(object, "server"), string(object, "world"),
                    string(object, "locale"), flag(object.get("vanished"))));
        }
    }

    public record Query(int id, List<UUID> players, List<String> names) {

        public Query {
            players = players == null ? List.of() : players.stream().filter(Objects::nonNull).distinct()
                    .limit(MAX_PLAYERS).toList();
            names = distinctNames(names == null ? List.of() : names);
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("id", id);
            JsonArray uuids = new JsonArray();
            players.forEach(player -> uuids.add(player.toString()));
            object.add("players", uuids);
            JsonArray typed = new JsonArray();
            names.forEach(typed::add);
            object.add("names", typed);
            return object;
        }

        public static Optional<Query> fromJson(JsonObject object) {
            if (object == null) {
                return Optional.empty();
            }
            List<UUID> uuids = new ArrayList<>();
            List<String> typed = new ArrayList<>();
            JsonElement players = object.get("players");
            if (players != null && players.isJsonArray()) {
                players.getAsJsonArray().forEach(element -> parseUuid(element).ifPresent(uuids::add));
            }
            JsonElement names = object.get("names");
            if (names != null && names.isJsonArray()) {
                names.getAsJsonArray().forEach(element -> {
                    if (element.isJsonPrimitive()) {
                        typed.add(element.getAsString());
                    }
                });
            }
            return Optional.of(new Query(PlayerWire.id(object), uuids, typed));
        }

        private static List<String> distinctNames(List<String> names) {
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                if (seen.add(trimmed.toLowerCase(Locale.ROOT)) && result.size() < MAX_NAMES) {
                    result.add(trimmed);
                }
            }
            return List.copyOf(result);
        }
    }

    public record State(String world, String nick) {

        public State {
            world = text(world);
            nick = text(nick);
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("world", world);
            object.addProperty("nick", nick);
            return object;
        }

        public static State fromJson(JsonObject object) {
            return new State(string(object, "world"), string(object, "nick"));
        }
    }

    public static JsonObject answer(int id, List<Live> players) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        JsonArray list = new JsonArray();
        players.stream().limit(MAX_PLAYERS).forEach(player -> list.add(player.toJson()));
        object.add("players", list);
        return object;
    }

    public static List<Live> players(JsonObject answer) {
        List<Live> players = new ArrayList<>();
        JsonElement list = answer == null ? null : answer.get("players");
        if (list != null && list.isJsonArray()) {
            list.getAsJsonArray().forEach(element -> Live.fromJson(element).ifPresent(players::add));
        }
        return List.copyOf(players);
    }

    public static int id(JsonObject message) {
        JsonElement id = message == null ? null : message.get("id");
        try {
            return id != null && id.isJsonPrimitive() ? id.getAsInt() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Optional<UUID> parseUuid(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(element.getAsString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static boolean flag(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
