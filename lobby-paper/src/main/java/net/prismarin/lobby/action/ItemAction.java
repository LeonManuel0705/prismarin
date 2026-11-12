package net.prismarin.lobby.action;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ItemAction(Type type, String argument) {

    public static final String MENU_NAVIGATOR = "navigator";
    public static final String MENU_PROFILE = "profile";
    public static final String MENU_LOBBIES = "lobbies";
    public static final String MENU_LANGUAGES = "languages";
    public static final String MENU_FRIENDS = "friends";
    public static final String MENU_PARTY = "party";
    public static final String MENU_CLAN = "clan";
    public static final String MENU_NICK = "nick";

    private static final Map<String, String> MENU_ALIASES = Map.ofEntries(
            Map.entry(MENU_NAVIGATOR, MENU_NAVIGATOR),
            Map.entry("compass", MENU_NAVIGATOR),
            Map.entry(MENU_PROFILE, MENU_PROFILE),
            Map.entry(MENU_LOBBIES, MENU_LOBBIES),
            Map.entry("lobby-selector", MENU_LOBBIES),
            Map.entry("selector", MENU_LOBBIES),
            Map.entry(MENU_LANGUAGES, MENU_LANGUAGES),
            Map.entry("language", MENU_LANGUAGES),
            Map.entry(MENU_FRIENDS, MENU_FRIENDS),
            Map.entry("friend", MENU_FRIENDS),
            Map.entry(MENU_PARTY, MENU_PARTY),
            Map.entry(MENU_CLAN, MENU_CLAN),
            Map.entry("clans", MENU_CLAN),
            Map.entry(MENU_NICK, MENU_NICK),
            Map.entry("nick-tool", MENU_NICK));

    public enum Type {
        PLAYER("player", "command", "cmd"),
        CONSOLE("console"),
        MESSAGE("message", "msg"),
        ACTIONBAR("actionbar"),
        MENU("menu"),
        SERVER("server", "connect"),
        GROUP("group", "play"),
        LOBBY("lobby"),
        SOUND("sound");

        private final List<String> ids;

        Type(String... ids) {
            this.ids = List.of(ids);
        }

        public String id() {
            return ids.getFirst();
        }

        public static Optional<Type> byId(String id) {
            String normalized = id.strip().toLowerCase(Locale.ROOT);
            for (Type type : values()) {
                if (type.ids.contains(normalized)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    public ItemAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(argument, "argument");
    }

    public static ItemAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("an action must not be empty");
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("'" + raw.strip() + "' needs the form 'type: value'");
        }
        String name = raw.substring(0, colon).strip();
        String argument = raw.substring(colon + 1).strip();
        Type type = Type.byId(name)
                .orElseThrow(() -> new IllegalArgumentException("unknown action type '" + name + "'"));
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("action '" + type.id() + "' needs a value");
        }
        return switch (type) {
            case PLAYER, CONSOLE -> new ItemAction(type, command(argument));
            case MENU -> new ItemAction(type, menu(argument));
            case SOUND -> {
                SoundSpec.parse(argument);
                yield new ItemAction(type, argument);
            }
            case SERVER, GROUP, LOBBY -> new ItemAction(type, single(type, argument));
            case MESSAGE, ACTIONBAR -> new ItemAction(type, argument);
        };
    }

    private static String command(String argument) {
        String command = argument.startsWith("/") ? argument.substring(1).strip() : argument;
        if (command.isEmpty()) {
            throw new IllegalArgumentException("a command action needs a command");
        }
        return command;
    }

    private static String menu(String argument) {
        String menu = MENU_ALIASES.get(argument.toLowerCase(Locale.ROOT));
        if (menu == null) {
            throw new IllegalArgumentException("unknown menu '" + argument
                    + "', use navigator, lobbies, profile, languages, friends, party or nick");
        }
        return menu;
    }

    private static String single(Type type, String argument) {
        if (argument.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("action '" + type.id() + "' takes a single name, got '" + argument + "'");
        }
        return argument;
    }
}
