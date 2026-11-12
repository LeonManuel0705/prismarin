package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.OptionalLong;
import net.prismarin.api.stats.StatFormat;
import org.junit.jupiter.api.Test;

class StatNumbersTest {

    private static OptionalLong auto(String text) {
        return StatNumbers.parse(text, StatFormat.NUMBER, StatNumbers.Decimal.AUTO);
    }

    @Test
    void plainAndGroupedNumbersAreRead() {
        assertEquals(OptionalLong.of(42L), auto("42"));
        assertEquals(OptionalLong.of(1_234L), auto("1,234"));
        assertEquals(OptionalLong.of(1_234L), auto("1.234"));
        assertEquals(OptionalLong.of(1_234_567L), auto("1.234.567"));
        assertEquals(OptionalLong.of(1_234_567L), auto("1 234 567"));
        assertEquals(OptionalLong.of(1_234_567L), auto("1 234 567"));
        assertEquals(OptionalLong.of(1_234_567L), auto("1'234'567"));
        assertEquals(OptionalLong.of(-5L), auto("-5"));
        assertEquals(OptionalLong.of(0L), auto("0"));
    }

    @Test
    void decimalsSuffixesAndTextAroundTheNumberAreHandled() {
        assertEquals(OptionalLong.of(1_235L), auto("1.234,5"));
        assertEquals(OptionalLong.of(1_235L), auto("1,234.5"));
        assertEquals(OptionalLong.of(2L), auto("1.5"));
        assertEquals(OptionalLong.of(1_500L), auto("1.5k"));
        assertEquals(OptionalLong.of(12_000_000L), auto("12M coins"));
        assertEquals(OptionalLong.of(12L), auto("Wins: 12"));
        assertEquals(OptionalLong.of(12L), auto("12 Kills"));
        assertEquals(OptionalLong.of(12L), auto("12Kills"));
        assertEquals(OptionalLong.of(7L), auto("&a7"));
        assertEquals(OptionalLong.of(7L), auto("§x§f§f§0§0§0§07"));
        assertEquals(OptionalLong.of(7L), auto("<green>7</green>"));
        assertEquals(OptionalLong.of(5L), auto("5."));
    }

    @Test
    void textWithoutANumberIsNoValue() {
        assertEquals(OptionalLong.empty(), auto(""));
        assertEquals(OptionalLong.empty(), auto("   "));
        assertEquals(OptionalLong.empty(), auto(null));
        assertEquals(OptionalLong.empty(), auto("N/A"));
        assertEquals(OptionalLong.empty(), auto("Loading..."));
        assertEquals(OptionalLong.empty(), auto("99999999999999999999999"));
    }

    @Test
    void theDecimalSeparatorCanBeFixed() {
        assertEquals(OptionalLong.of(1L), StatNumbers.parse("1.234", StatFormat.NUMBER, StatNumbers.Decimal.DOT));
        assertEquals(OptionalLong.of(1_234L),
                StatNumbers.parse("1,234", StatFormat.NUMBER, StatNumbers.Decimal.DOT));
        assertEquals(OptionalLong.of(1L), StatNumbers.parse("1,234", StatFormat.NUMBER, StatNumbers.Decimal.COMMA));
        assertEquals(OptionalLong.of(1_234L),
                StatNumbers.parse("1.234", StatFormat.NUMBER, StatNumbers.Decimal.COMMA));
        assertEquals(Optional.of(StatNumbers.Decimal.COMMA), StatNumbers.Decimal.byId("comma"));
        assertEquals(Optional.of(StatNumbers.Decimal.DOT), StatNumbers.Decimal.byId("."));
        assertEquals(Optional.empty(), StatNumbers.Decimal.byId("semicolon"));
    }

    @Test
    void formatsChangeHowAValueIsStored() {
        assertEquals(OptionalLong.of(125L), StatNumbers.parse("1.25", StatFormat.DECIMAL, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(125L), StatNumbers.parse("1,25", StatFormat.DECIMAL, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(125L), StatNumbers.parse("1.250", StatFormat.DECIMAL, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(3_723L),
                StatNumbers.parse("1:02:03", StatFormat.DURATION, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(62_345L),
                StatNumbers.parse("1:02.345", StatFormat.MILLIS, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(5_400L),
                StatNumbers.parse("1h 30m", StatFormat.DURATION, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(90_061L),
                StatNumbers.parse("1d 1h 1m 1s", StatFormat.DURATION, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(45L), StatNumbers.parse("45", StatFormat.DURATION, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(750L), StatNumbers.parse("12:30", StatFormat.NUMBER, StatNumbers.Decimal.AUTO));
        assertEquals(OptionalLong.of(5L), StatNumbers.parse("Ratio 5:3", StatFormat.NUMBER,
                StatNumbers.Decimal.AUTO));
    }
}
