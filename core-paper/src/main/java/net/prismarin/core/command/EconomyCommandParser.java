package net.prismarin.core.command;

import java.util.List;
import java.util.Locale;
import net.prismarin.api.economy.Currency;

public final class EconomyCommandParser {

    public enum Action {
        GIVE,
        TAKE,
        SET
    }

    public sealed interface Result permits Parsed, Invalid {
    }

    public record Parsed(Action action, String targetName, Currency currency, long amount) implements Result {
    }

    public record Invalid(String messageKey) implements Result {
    }

    private EconomyCommandParser() {
        throw new AssertionError();
    }

    public static Result parse(String[] args, List<Currency> currencies) {
        if (args.length != 4) {
            return new Invalid("economy.usage");
        }
        Action action = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> Action.GIVE;
            case "take" -> Action.TAKE;
            case "set" -> Action.SET;
            default -> null;
        };
        if (action == null) {
            return new Invalid("economy.usage");
        }
        String requested = args[2].toLowerCase(Locale.ROOT);
        Currency currency = currencies.stream()
                .filter(candidate -> candidate.id().equals(requested))
                .findFirst()
                .orElse(null);
        if (currency == null) {
            return new Invalid("economy.invalid-currency");
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            return new Invalid("economy.invalid-amount");
        }
        if (amount < 0 || (action != Action.SET && amount == 0)) {
            return new Invalid("economy.invalid-amount");
        }
        return new Parsed(action, args[1], currency, amount);
    }
}
