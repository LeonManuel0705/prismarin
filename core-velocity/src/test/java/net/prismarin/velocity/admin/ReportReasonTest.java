package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ReportReasonTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void picksTheViewersLanguageThenEnglishThenTheFirstName() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("fr", "Triche");
        names.put("de", "Cheats");
        ReportReason reason = new ReportReason("Hacking", null, names);
        assertEquals("hacking", reason.id());
        assertEquals("PAPER", reason.icon());
        assertEquals("Cheats", reason.name(Locale.GERMANY));
        assertEquals("Triche", reason.name(Locale.ITALIAN));
        assertEquals("Hacking", new ReportReason("other", "BOOK", Map.of("en", "Hacking")).name(Locale.GERMAN));
        assertEquals("other", new ReportReason("other", "BOOK", Map.of()).name(Locale.GERMAN));
    }

    @Test
    void chatEvidenceFollowsTheConfiguredReasons() {
        AdminSettings.ChatEvidence evidence = new AdminSettings.ChatEvidence(true, 15, 40, false,
                List.of("Chat"), false);
        assertEquals(true, evidence.appliesTo("chat"));
        assertEquals(false, evidence.appliesTo("hacking"));
        assertEquals(false, evidence.appliesTo(null));
        assertEquals(900_000L, evidence.windowMillis());
    }

    @Test
    void namesAreColouredAndMayCarryTheirOwnFormatting() {
        ReportReason hacking = new ReportReason("hacking", "DIAMOND_SWORD",
                Map.of("en", "&lHacking", "de", "<i>Cheats</i>"), NamedTextColor.RED);
        Component english = hacking.display(Locale.ENGLISH);
        assertEquals("Hacking", PLAIN.serialize(english));
        assertEquals(NamedTextColor.RED, english.color());
        assertEquals(TextDecoration.State.TRUE, textStyle(english).decoration(TextDecoration.BOLD));
        assertEquals("Hacking", hacking.plain(Locale.ENGLISH));
        assertEquals("Cheats", hacking.plain(Locale.GERMAN));
        ReportReason own = new ReportReason("chat", "BOOK", Map.of("en", "<#00FF00>Chat"), NamedTextColor.GOLD);
        assertEquals(TextColor.color(0x00FF00), textStyle(own.display(Locale.ENGLISH)).color());
        assertEquals(null, new ReportReason("other", "BOOK", Map.of("en", "Other")).display(Locale.ENGLISH).color());
    }

    @Test
    void storedReasonsAreShownInTheViewersLanguageWithTheirColour() {
        List<ReportReason> reasons = List.of(
                new ReportReason("spam", "PAPER", Map.of("en", "Spam", "de", "Spam"), NamedTextColor.YELLOW),
                new ReportReason("ads", "PAPER", Map.of("en", "Spam or advertising", "de", "Spam oder Werbung"),
                        NamedTextColor.AQUA),
                new ReportReason("hacking", "PAPER", Map.of("en", "&cHacking or cheats", "de", "Hacking oder Cheats"),
                        NamedTextColor.RED));
        Component ads = ReportReason.describe("Spam or advertising: discord link", reasons, Locale.GERMAN);
        assertEquals("Spam oder Werbung: discord link", PLAIN.serialize(ads));
        assertEquals(NamedTextColor.AQUA, ads.color());
        Component spam = ReportReason.describe("spam", reasons, Locale.ENGLISH);
        assertEquals("Spam", PLAIN.serialize(spam));
        assertEquals(NamedTextColor.YELLOW, spam.color());
        assertEquals("Hacking or cheats", PLAIN.serialize(ReportReason.describe("Hacking oder Cheats", reasons,
                Locale.ENGLISH)));
        Component free = ReportReason.describe("Spamming the chat", reasons, Locale.ENGLISH);
        assertEquals("Spamming the chat", PLAIN.serialize(free));
        assertEquals(null, free.color());
        assertEquals("", PLAIN.serialize(ReportReason.describe(null, reasons, Locale.ENGLISH)));
    }

    private static Style textStyle(Component component) {
        return textStyle(component, Style.empty());
    }

    private static Style textStyle(Component component, Style inherited) {
        Style style = inherited.merge(component.style());
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            return style;
        }
        return component.children().isEmpty() ? style : textStyle(component.children().getFirst(), style);
    }
}
