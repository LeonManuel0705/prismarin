package net.prismarin.core.command;

import java.util.Locale;

public final class LevelCommandParser {

    public enum Action {
        SET,
        ADD,
        REMOVE,
        ADD_XP
    }

    public sealed interface Result permits Show, Change, Invalid {
    }

    public record Show(String targetName) implements Result {
    }

    public record Change(String targetName, Action action, long amount) implements Result {
    }

    public record Invalid(String messageKey) implements Result {
    }

    private static final int MAX_LEVEL = 1_000_000;

    private LevelCommandParser() {
        throw new AssertionError();
    }

    public static Result parse(String[] args) {
        if (args.length == 1) {
            return new Show(args[0]);
        }
        if (args.length != 3) {
            return new Invalid("level.usage");
        }
        Action action = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> Action.SET;
            case "add", "give" -> Action.ADD;
            case "remove", "take" -> Action.REMOVE;
            case "addxp", "xp" -> Action.ADD_XP;
            default -> null;
        };
        if (action == null) {
            return new Invalid("level.usage");
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            return new Invalid("level.invalid-amount");
        }
        if (amount < 0 || (action != Action.SET && amount == 0) || (action != Action.ADD_XP && amount > MAX_LEVEL)) {
            return new Invalid("level.invalid-amount");
        }
        return new Change(args[0], action, amount);
    }

    public static int apply(int level, Action action, long amount) {
        long result = switch (action) {
            case SET -> amount;
            case ADD -> (long) level + amount;
            case REMOVE -> (long) level - amount;
            case ADD_XP -> level;
        };
        return (int) Math.max(0L, Math.min(MAX_LEVEL, result));
    }
}
