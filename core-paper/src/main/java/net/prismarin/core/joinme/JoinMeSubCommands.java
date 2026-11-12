package net.prismarin.core.joinme;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public record JoinMeSubCommands(Set<String> off) {

    public static final String USE_PERMISSION = "prismarin.command.joinme";
    public static final String CREATE_PERMISSION = "prismarin.joinme";
    public static final String CANCEL_PERMISSION = "prismarin.joinme.cancel";
    public static final String CREATE = "create";
    public static final String LIST = "list";
    public static final String CANCEL = "cancel";
    public static final String PREVIEW = "preview";
    public static final String HELP = "help";
    public static final String JOIN = "join";
    public static final List<String> SWITCHES = List.of(LIST, CANCEL, PREVIEW);
    public static final JoinMeSubCommands ALL = new JoinMeSubCommands(Set.of());

    private static final List<String> ORDER = List.of(CREATE, PREVIEW, LIST, CANCEL, HELP);

    public JoinMeSubCommands {
        Set<String> known = new LinkedHashSet<>();
        for (String key : off == null ? Set.<String>of() : off) {
            String lowered = key.toLowerCase(Locale.ROOT);
            if (SWITCHES.contains(lowered)) {
                known.add(lowered);
            }
        }
        off = Set.copyOf(known);
    }

    public boolean on(String action) {
        return !off.contains(action);
    }

    public boolean allowed(String action, Predicate<String> permissions) {
        return switch (action) {
            case CREATE -> permissions.test(CREATE_PERMISSION);
            case PREVIEW -> on(PREVIEW) && permissions.test(CREATE_PERMISSION);
            case LIST -> on(LIST);
            case CANCEL -> on(CANCEL) && (permissions.test(CREATE_PERMISSION) || permissions.test(CANCEL_PERMISSION));
            case HELP -> true;
            case JOIN -> true;
            default -> false;
        };
    }

    public boolean cancelsOthers(Predicate<String> permissions) {
        return on(CANCEL) && permissions.test(CANCEL_PERMISSION);
    }

    public List<String> visible(Predicate<String> permissions) {
        List<String> visible = new ArrayList<>();
        for (String action : ORDER) {
            if (allowed(action, permissions)) {
                visible.add(action);
            }
        }
        return List.copyOf(visible);
    }

    public List<String> suggestions(Predicate<String> permissions, String typed) {
        String start = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String action : visible(permissions)) {
            if (!CREATE.equals(action) && action.startsWith(start)) {
                found.add(action);
            }
        }
        return List.copyOf(found);
    }

    public String fallback(Predicate<String> permissions) {
        if (allowed(CREATE, permissions)) {
            return CREATE;
        }
        return allowed(LIST, permissions) ? LIST : HELP;
    }

    public static boolean hidden(String action) {
        return JOIN.equalsIgnoreCase(action);
    }
}
