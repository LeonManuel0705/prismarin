package net.prismarin.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import net.prismarin.api.economy.Currency;
import org.junit.jupiter.api.Test;

class EconomyCommandParserTest {

    private static final List<Currency> CURRENCIES = List.of(Currency.COINS, Currency.GEMS);

    @Test
    void parsesValidGiveCommand() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "500"}, CURRENCIES);
        EconomyCommandParser.Parsed parsed = assertInstanceOf(EconomyCommandParser.Parsed.class, result);
        assertEquals(EconomyCommandParser.Action.GIVE, parsed.action());
        assertEquals("Steve", parsed.targetName());
        assertEquals(Currency.COINS, parsed.currency());
        assertEquals(500L, parsed.amount());
    }

    @Test
    void parsesValidTakeGemsCommand() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"take", "Alex", "gems", "25"}, CURRENCIES);
        EconomyCommandParser.Parsed parsed = assertInstanceOf(EconomyCommandParser.Parsed.class, result);
        assertEquals(EconomyCommandParser.Action.TAKE, parsed.action());
        assertEquals(Currency.GEMS, parsed.currency());
        assertEquals(25L, parsed.amount());
    }

    @Test
    void actionAndCurrencyAreCaseInsensitive() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"GIVE", "Steve", "Coins", "1"}, CURRENCIES);
        EconomyCommandParser.Parsed parsed = assertInstanceOf(EconomyCommandParser.Parsed.class, result);
        assertEquals(EconomyCommandParser.Action.GIVE, parsed.action());
        assertEquals(Currency.COINS, parsed.currency());
    }

    @Test
    void rejectsTooFewArguments() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "coins"}, CURRENCIES);
        assertEquals("economy.usage", assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsTooManyArguments() {
        EconomyCommandParser.Result result =
                EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "1", "extra"}, CURRENCIES);
        assertEquals("economy.usage", assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsUnknownAction() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"steal", "Steve", "coins", "1"}, CURRENCIES);
        assertEquals("economy.usage", assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsUnknownCurrency() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "euro", "1"}, CURRENCIES);
        assertEquals("economy.invalid-currency",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsNonNumericAmount() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "viel"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsNegativeAmount() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "-5"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsZeroAmountForGive() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "0"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsZeroAmountForTake() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"take", "Steve", "coins", "0"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void allowsZeroAmountForSet() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"set", "Steve", "gems", "0"}, CURRENCIES);
        EconomyCommandParser.Parsed parsed = assertInstanceOf(EconomyCommandParser.Parsed.class, result);
        assertEquals(EconomyCommandParser.Action.SET, parsed.action());
        assertEquals(0L, parsed.amount());
    }

    @Test
    void rejectsNegativeAmountForSet() {
        EconomyCommandParser.Result result = EconomyCommandParser.parse(new String[]{"set", "Steve", "gems", "-1"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }

    @Test
    void rejectsAmountBeyondLongRange() {
        EconomyCommandParser.Result result =
                EconomyCommandParser.parse(new String[]{"give", "Steve", "coins", "99999999999999999999"}, CURRENCIES);
        assertEquals("economy.invalid-amount",
                assertInstanceOf(EconomyCommandParser.Invalid.class, result).messageKey());
    }
}
