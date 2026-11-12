package net.prismarin.core.stats;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Predicate;

public final class StatsArguments {

    public static final int MAX_PAGE = 999;

    public sealed interface Result {
    }

    public record Show(String player, String game, int page) implements Result {

        public boolean self() {
            return player == null;
        }
    }

    public record Usage() implements Result {
    }

    private StatsArguments() {
        throw new AssertionError();
    }

    public static Result parse(String[] args, Predicate<String> games, Predicate<String> players) {
        if (args == null || args.length == 0) {
            return new Show(null, null, 1);
        }
        if (args.length > 3) {
            return new Usage();
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length <= 2 && games.test(first) && !players.test(args[0])) {
            if (args.length == 1) {
                return new Show(null, first, 1);
            }
            OptionalInt page = page(args[1]);
            return page.isPresent() ? new Show(null, first, page.getAsInt()) : new Usage();
        }
        String player = args[0];
        if (args.length == 1) {
            return new Show(player, null, 1);
        }
        String second = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            OptionalInt page = page(second);
            if (page.isPresent() && !games.test(second)) {
                return new Show(player, null, page.getAsInt());
            }
            return new Show(player, second, 1);
        }
        OptionalInt page = page(args[2]);
        return page.isPresent() ? new Show(player, second, page.getAsInt()) : new Usage();
    }

    public static String command(String player, String game, int page) {
        StringBuilder command = new StringBuilder("/stats ").append(player);
        if (game != null) {
            command.append(' ').append(game);
        }
        if (page > 1) {
            command.append(' ').append(page);
        }
        return command.toString();
    }

    private static OptionalInt page(String text) {
        if (text == null || text.isEmpty() || text.length() > 3 || !text.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        int value = Integer.parseInt(text);
        return value < 1 || value > MAX_PAGE ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
