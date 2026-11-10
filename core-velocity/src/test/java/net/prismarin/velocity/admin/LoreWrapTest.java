package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class LoreWrapTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(PLAIN::serialize).toList();
    }

    @Test
    void shortLinesStayAsTheyAre() {
        Component line = MiniMessage.miniMessage().deserialize("<gray>Next level:</gray> <gold>Ban 30 days</gold>");
        List<Component> wrapped = LoreWrap.wrap(line, 34);
        assertEquals(List.of("Next level: Ban 30 days"), plain(wrapped));
    }

    @Test
    void longLinesBreakAtSpacesAndKeepTheirColours() {
        Component line = MiniMessage.miniMessage().deserialize(
                "<gray>Reasons:</gray> <red>Hacking or cheats</red><dark_gray>,</dark_gray> "
                        + "<gold>Insults in chat</gold><dark_gray>,</dark_gray> <yellow>Spam or advertising</yellow>");
        List<Component> wrapped = LoreWrap.wrap(line, 20);
        assertEquals(List.of("Reasons: Hacking or", "cheats, Insults in", "chat, Spam or", "advertising"),
                plain(wrapped));
        wrapped.forEach(part -> assertTrue(PLAIN.serialize(part).length() <= 20));
        Component second = wrapped.get(1);
        assertEquals(NamedTextColor.RED, second.children().getFirst().color());
        assertEquals(NamedTextColor.YELLOW, wrapped.get(3).children().getFirst().color());
    }

    @Test
    void stylesOfParentsReachTheirChildren() {
        Component line = Component.text().style(Style.style(NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text("alpha beta "), Component.text("gamma", NamedTextColor.WHITE)).build();
        List<Component> wrapped = LoreWrap.wrap(line, 11);
        assertEquals(List.of("alpha beta", "gamma"), plain(wrapped));
        Component gamma = wrapped.get(1).children().getFirst();
        assertEquals(NamedTextColor.WHITE, gamma.color());
        assertEquals(TextDecoration.State.TRUE, gamma.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GRAY, wrapped.getFirst().children().getFirst().color());
    }

    @Test
    void wordsLongerThanALineAreCutAndNewlinesBreak() {
        List<Component> cut = LoreWrap.wrap(Component.text("abcdefghijklmnopqrstuvwxyz"), 10);
        assertEquals(List.of("abcdefghij", "klmnopqrst", "uvwxyz"), plain(cut));
        List<Component> lines = LoreWrap.wrap(Component.text("first\nsecond line"), 30);
        assertEquals(List.of("first", "second line"), plain(lines));
    }

    @Test
    void emptyLinesStayAsSpacersAndLeadingSpacesSurviveOnlyOnTheFirstLine() {
        assertEquals(List.of(""), plain(LoreWrap.wrap(Component.empty(), 20)));
        assertEquals(List.of(" » one two", "three"), plain(LoreWrap.wrap(Component.text(" » one two three"), 10)));
        assertEquals(List.of("a", "", "b"), plain(LoreWrap.wrapAll(List.of(Component.text("a"), Component.empty(),
                Component.text("b")), 20)));
    }
}
