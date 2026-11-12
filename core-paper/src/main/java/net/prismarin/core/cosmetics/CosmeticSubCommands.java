package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public record CosmeticSubCommands(boolean hide) {

    public static final String USE_PERMISSION = "prismarin.command.cosmetics";
    public static final String GIVE_PERMISSION = "prismarin.command.cosmetics.give";
    public static final String TAKE_PERMISSION = "prismarin.command.cosmetics.take";
    public static final String PLAY_PERMISSION = "prismarin.command.cosmetics.play";
    public static final String HIDE = "hide";
    public static final String GIVE = "give";
    public static final String TAKE = "take";
    public static final String PLAY = "play";
    public static final String HELP = "help";
    public static final CosmeticSubCommands ALL = new CosmeticSubCommands(true);

    private static final List<String> ORDER = List.of(HIDE, GIVE, TAKE, HELP);

    public boolean allowed(String action, Predicate<String> permissions) {
        return switch (action) {
            case HIDE -> hide && permissions.test(USE_PERMISSION);
            case GIVE -> permissions.test(GIVE_PERMISSION);
            case TAKE -> permissions.test(TAKE_PERMISSION);
            case PLAY -> permissions.test(PLAY_PERMISSION);
            case HELP -> true;
            default -> false;
        };
    }

    public boolean opens(Predicate<String> permissions) {
        return permissions.test(USE_PERMISSION);
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

    public List<String> suggestions(Predicate<String> permissions, String typed, List<String> categories) {
        String start = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        if (opens(permissions)) {
            for (String category : categories) {
                if (category.startsWith(start)) {
                    found.add(category);
                }
            }
        }
        for (String action : visible(permissions)) {
            if (action.startsWith(start) && !found.contains(action)) {
                found.add(action);
            }
        }
        return List.copyOf(found);
    }

    public static String action(String typed) {
        return typed == null ? "" : typed.toLowerCase(Locale.ROOT);
    }
}
