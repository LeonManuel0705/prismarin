package net.prismarin.velocity.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record SocialRequest(int id, Action action, UUID target, SocialSetting setting, String value, View view,
                            int page, Map<PartyOption, String> options) {

    public static final int MAX_PAGE = 1_000;

    public SocialRequest {
        options = options == null || options.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new EnumMap<>(options));
    }

    public enum Action {
        HELLO("hello", false),
        VIEW("view", false),
        FRIEND_ADD("friend-add", true),
        FRIEND_ACCEPT("friend-accept", true),
        FRIEND_DENY("friend-deny", true),
        FRIEND_CANCEL("friend-cancel", true),
        FRIEND_REMOVE("friend-remove", true),
        FRIEND_JUMP("friend-jump", true),
        FRIEND_REQUEST("friend-request", true),
        PARTY_INVITE("party-invite", true),
        PARTY_ACCEPT("party-accept", true),
        PARTY_DENY("party-deny", true),
        PARTY_JOIN("party-join", true),
        PARTY_LEAVE("party-leave", false),
        PARTY_KICK("party-kick", true),
        PARTY_PROMOTE("party-promote", true),
        PARTY_DEMOTE("party-demote", true),
        PARTY_TRANSFER("party-transfer", true),
        PARTY_DISBAND("party-disband", false),
        PARTY_WARP("party-warp", false),
        PARTY_SETTING("party-setting", false),
        SETTING("setting", false);

        private final String id;
        private final boolean needsTarget;

        Action(String id, boolean needsTarget) {
            this.id = id;
            this.needsTarget = needsTarget;
        }

        public String id() {
            return id;
        }

        public boolean needsTarget() {
            return needsTarget;
        }

        static Optional<Action> byId(String id) {
            for (Action action : values()) {
                if (action.id.equals(id)) {
                    return Optional.of(action);
                }
            }
            return Optional.empty();
        }
    }

    public enum View {
        NONE("none"),
        FRIENDS("friends"),
        REQUESTS("requests"),
        PARTY("party"),
        PARTY_INVITE("party-invite"),
        SETTINGS("settings");

        private final String id;

        View(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Optional<View> byId(String id) {
            for (View view : values()) {
                if (view.id.equals(id)) {
                    return Optional.of(view);
                }
            }
            return Optional.empty();
        }
    }

    public static Optional<SocialRequest> parse(JsonObject message) {
        try {
            Optional<Action> action = text(message, "action").flatMap(Action::byId);
            if (action.isEmpty()) {
                return Optional.empty();
            }
            int id = message.has("id") ? message.get("id").getAsInt() : 0;
            Optional<String> rawView = text(message, "view");
            View view = View.NONE;
            if (rawView.isPresent()) {
                Optional<View> found = View.byId(rawView.get());
                if (found.isEmpty()) {
                    return Optional.empty();
                }
                view = found.get();
            }
            int page = message.has("page") ? message.get("page").getAsInt() : 0;
            if (page < 0 || page > MAX_PAGE) {
                return Optional.empty();
            }
            UUID target = null;
            if (action.get().needsTarget()) {
                Optional<String> raw = text(message, "target");
                if (raw.isEmpty()) {
                    return Optional.empty();
                }
                target = UUID.fromString(raw.get());
            }
            SocialSetting setting = null;
            String value = null;
            if (action.get() == Action.SETTING) {
                Optional<SocialSetting> found = text(message, "key").flatMap(SocialSetting::byId);
                if (found.isEmpty()) {
                    return Optional.empty();
                }
                Optional<String> normalized = text(message, "value").flatMap(found.get()::normalize);
                if (normalized.isEmpty()) {
                    return Optional.empty();
                }
                setting = found.get();
                value = normalized.get();
            }
            Map<PartyOption, String> options = Map.of();
            if (action.get() == Action.PARTY_SETTING) {
                Optional<Map<PartyOption, String>> parsed = options(message.get("options"));
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                options = parsed.get();
            }
            return Optional.of(new SocialRequest(id, action.get(), target, setting, value, view, page, options));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Map<PartyOption, String>> options(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        if (object.size() == 0 || object.size() > PartyOption.values().length) {
            return Optional.empty();
        }
        Map<PartyOption, String> options = new EnumMap<>(PartyOption.class);
        for (String key : object.keySet()) {
            Optional<PartyOption> option = PartyOption.byId(key);
            Optional<String> value = option.flatMap(found -> text(object, key).flatMap(found::normalize));
            if (value.isEmpty()) {
                return Optional.empty();
            }
            options.put(option.get(), value.get());
        }
        return Optional.of(options);
    }

    private static Optional<String> text(JsonObject message, String field) {
        JsonElement element = message.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value.length() > 64 ? Optional.empty() : Optional.of(value.toLowerCase(Locale.ROOT));
    }
}
