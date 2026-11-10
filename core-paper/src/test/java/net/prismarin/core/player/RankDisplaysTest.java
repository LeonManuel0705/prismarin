package net.prismarin.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.player.RankDisplay;
import org.junit.jupiter.api.Test;

class RankDisplaysTest {

    @Test
    void labelIsThePlainPrefixWithoutPadding() {
        assertEquals("Admin", RankDisplays.label(MiniMessage.miniMessage().deserialize("<red><b>Admin</b></red> ")));
        assertEquals("[VIP]", RankDisplays.label(Component.text(" [VIP] ", NamedTextColor.GOLD)));
        assertEquals("", RankDisplays.label(null));
    }

    @Test
    void displaysKeepEveryPart() {
        Component prefix = Component.text("Mod ", NamedTextColor.BLUE);
        RankDisplay display = RankDisplays.of(prefix, Component.text("*"), NamedTextColor.BLUE, null, 50);
        assertEquals("Mod", display.label());
        assertEquals(prefix, display.prefix());
        assertEquals(Component.text("*"), display.suffix());
        assertEquals(50, display.weight());
        assertEquals(NamedTextColor.WHITE, display.chatColorOr(NamedTextColor.WHITE));
    }
}
