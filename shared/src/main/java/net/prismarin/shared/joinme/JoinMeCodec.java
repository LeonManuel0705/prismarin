package net.prismarin.shared.joinme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.server.GameState;

public final class JoinMeCodec {

    public static final Pattern ID = Pattern.compile("[a-z0-9]{4,16}");
    public static final Pattern PLACEHOLDER_KEY = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_PLACEHOLDERS = 16;
    public static final int MAX_VALUE = 96;
    private static final int MAX_TEXT = 64;

    private JoinMeCodec() {
        throw new AssertionError();
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static Map<String, String> cleanPlaceholders(Map<String, String> values) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (values == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (clean.size() >= MAX_PLACEHOLDERS) {
                break;
            }
            String key = entry.getKey() == null ? "" : entry.getKey().strip().toLowerCase(Locale.ROOT);
            String value = entry.getValue();
            if (!PLACEHOLDER_KEY.matcher(key).matches() || value == null) {
                continue;
            }
            clean.put(key, clip(value.replaceAll("\\p{Cntrl}", " ").strip(), MAX_VALUE));
        }
        return Collections.unmodifiableMap(clean);
    }

    public static JsonObject encode(JoinMeAudience audience) {
        JsonObject object = new JsonObject();
        object.addProperty("scope", audience.scope().id());
        JsonArray groups = new JsonArray();
        audience.groups().stream().sorted().forEach(groups::add);
        object.add("groups", groups);
        JsonArray circles = new JsonArray();
        audience.circles().stream().sorted().forEach(circle -> circles.add(circle.id()));
        object.add("circles", circles);
        return object;
    }

    public static Optional<JoinMeAudience> audience(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        JoinMeAudience.Scope scope = JoinMeAudience.Scope.byId(text(object, "scope")).orElse(JoinMeAudience.Scope.NETWORK);
        Set<String> groups = new LinkedHashSet<>();
        Set<JoinMeAudience.Circle> circles = new LinkedHashSet<>();
        JsonElement groupList = object.get("groups");
        if (groupList != null && groupList.isJsonArray()) {
            groupList.getAsJsonArray().forEach(item -> {
                if (item.isJsonPrimitive() && groups.size() < MAX_PLACEHOLDERS * 4) {
                    groups.add(clip(item.getAsString(), MAX_TEXT));
                }
            });
        }
        JsonElement circleList = object.get("circles");
        if (circleList != null && circleList.isJsonArray()) {
            circleList.getAsJsonArray().forEach(item -> {
                if (item.isJsonPrimitive()) {
                    JoinMeAudience.Circle.byId(item.getAsString()).ifPresent(circles::add);
                }
            });
        }
        return Optional.of(new JoinMeAudience(scope, groups, circles));
    }

    public static JsonObject encode(JoinMeSpot spot) {
        JsonObject object = new JsonObject();
        object.addProperty("creator", spot.creator().toString());
        object.addProperty("server", spot.server());
        object.addProperty("group", spot.group());
        object.addProperty("world", spot.world());
        object.addProperty("state", spot.state().name());
        return object;
    }

    public static Optional<JoinMeSpot> spot(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        return uuid(object, "creator").map(creator -> new JoinMeSpot(creator, clip(text(object, "server"), MAX_TEXT),
                clip(text(object, "group"), MAX_TEXT), clip(text(object, "world"), MAX_TEXT),
                state(text(object, "state"))));
    }

    public static JsonObject encode(JoinMe joinMe) {
        JsonObject object = new JsonObject();
        object.addProperty("id", joinMe.id());
        object.addProperty("creator", joinMe.creator().toString());
        object.addProperty("creator-name", joinMe.creatorName());
        object.addProperty("shown-name", joinMe.shownName());
        object.addProperty("server", joinMe.server());
        object.addProperty("group", joinMe.group());
        object.addProperty("world", joinMe.world());
        object.addProperty("state", joinMe.state().name());
        object.addProperty("handler", joinMe.handler());
        object.add("placeholders", placeholders(joinMe.placeholders()));
        object.add("audience", encode(joinMe.audience()));
        object.addProperty("max-joins", joinMe.maxJoins());
        object.addProperty("joins", joinMe.joins());
        object.addProperty("created", joinMe.createdAt());
        object.addProperty("expires", joinMe.expiresAt());
        return object;
    }

    public static Optional<JoinMe> joinMe(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        try {
            JsonObject object = element.getAsJsonObject();
            String id = text(object, "id");
            Optional<UUID> creator = uuid(object, "creator");
            if (!validId(id) || creator.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new JoinMe(id, creator.get(), clip(text(object, "creator-name"), MAX_TEXT),
                    clip(text(object, "shown-name"), MAX_TEXT), clip(text(object, "server"), MAX_TEXT),
                    clip(text(object, "group"), MAX_TEXT), clip(text(object, "world"), MAX_TEXT),
                    state(text(object, "state")), clip(text(object, "handler"), MAX_TEXT),
                    placeholders(object.get("placeholders")),
                    audience(object.get("audience")).orElse(JoinMeAudience.NETWORK), number(object, "max-joins"),
                    number(object, "joins"), longNumber(object, "created"), longNumber(object, "expires")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static JsonObject encode(JoinMeDraft draft) {
        JsonObject object = new JsonObject();
        object.add("spot", encode(draft.spot()));
        object.addProperty("shown-name", draft.shownName());
        object.addProperty("handler", draft.handler());
        object.addProperty("world-fallback", draft.worldFallback());
        object.add("placeholders", placeholders(draft.placeholders()));
        object.addProperty("lifetime", draft.lifetimeMillis());
        object.addProperty("max-joins", draft.maxJoins());
        if (draft.audience() != null) {
            object.add("audience", encode(draft.audience()));
        }
        object.addProperty("ignore-cooldown", draft.ignoreCooldown());
        object.addProperty("free", draft.free());
        object.add("look", encode(draft.look()));
        return object;
    }

    public static JsonObject encode(JoinMeLook look) {
        JsonObject object = new JsonObject();
        object.addProperty("texture", look.texture());
        JsonObject lines = new JsonObject();
        look.lines().forEach((row, text) -> lines.addProperty(Integer.toString(row), text));
        object.add("lines", lines);
        return object;
    }

    public static JoinMeLook look(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return JoinMeLook.NONE;
        }
        JsonObject object = element.getAsJsonObject();
        Map<Integer, String> lines = new LinkedHashMap<>();
        JsonElement rows = object.get("lines");
        if (rows != null && rows.isJsonObject()) {
            rows.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().matches("\\d{1,2}") && entry.getValue().isJsonPrimitive()) {
                    lines.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                }
            });
        }
        return new JoinMeLook(text(object, "texture"), lines);
    }

    public static Optional<JoinMeDraft> draft(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        return spot(object.get("spot")).map(spot -> new JoinMeDraft(spot, clip(text(object, "shown-name"), MAX_TEXT),
                clip(text(object, "handler"), MAX_TEXT), flag(object, "world-fallback"),
                placeholders(object.get("placeholders")), longNumber(object, "lifetime"), object.has("max-joins") ? number(object, "max-joins") : -1,
                audience(object.get("audience")).orElse(null), flag(object, "ignore-cooldown"),
                flag(object, "free"), look(object.get("look"))));
    }

    public static JsonObject encode(JoinMeTerms terms) {
        JsonObject object = new JsonObject();
        object.addProperty("status", terms.status().id());
        object.addProperty("wait", terms.waitMillis());
        object.addProperty("currency", terms.currency());
        object.addProperty("cost", terms.cost());
        object.addProperty("lifetime", terms.lifetimeMillis());
        object.addProperty("max-joins", terms.maxJoins());
        object.add("audience", encode(terms.audience()));
        object.addProperty("same-server", terms.sameServer());
        return object;
    }

    public static JoinMeTerms terms(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return JoinMeTerms.refused(JoinMeResult.Status.FAILED);
        }
        JsonObject object = element.getAsJsonObject();
        return new JoinMeTerms(status(text(object, "status")), longNumber(object, "wait"),
                clip(text(object, "currency"), MAX_TEXT), longNumber(object, "cost"), longNumber(object, "lifetime"),
                number(object, "max-joins"), audience(object.get("audience")).orElse(null),
                !object.has("same-server") || flag(object, "same-server"));
    }

    public static JsonObject encode(JoinMeResult result) {
        JsonObject object = new JsonObject();
        object.addProperty("status", result.status().id());
        object.addProperty("wait", result.waitMillis());
        result.joinMe().ifPresent(joinMe -> object.add("joinme", encode(joinMe)));
        return object;
    }

    public static JoinMeResult result(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return JoinMeResult.of(JoinMeResult.Status.FAILED);
        }
        JsonObject object = element.getAsJsonObject();
        return new JoinMeResult(status(text(object, "status")), joinMe(object.get("joinme")),
                longNumber(object, "wait"));
    }

    private static JsonObject placeholders(Map<String, String> values) {
        JsonObject object = new JsonObject();
        cleanPlaceholders(values).forEach(object::addProperty);
        return object;
    }

    private static Map<String, String> placeholders(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        element.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonPrimitive()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        });
        return cleanPlaceholders(values);
    }

    private static JoinMeResult.Status status(String id) {
        return JoinMeResult.Status.byId(id).orElse(JoinMeResult.Status.FAILED);
    }

    private static GameState state(String name) {
        try {
            return GameState.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GameState.UNKNOWN;
        }
    }

    private static Optional<UUID> uuid(JsonObject object, String key) {
        try {
            return Optional.of(UUID.fromString(text(object, key)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static int number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return value.getAsInt();
    }

    private static long longNumber(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        return value.getAsLong();
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
