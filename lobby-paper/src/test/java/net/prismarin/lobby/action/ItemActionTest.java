package net.prismarin.lobby.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.prismarin.lobby.config.CustomItem;
import org.junit.jupiter.api.Test;

class ItemActionTest {

    @Test
    void parsesEveryActionType() {
        assertEquals(new ItemAction(ItemAction.Type.PLAYER, "spawn"), ItemAction.parse("player: spawn"));
        assertEquals(new ItemAction(ItemAction.Type.CONSOLE, "give %player% diamond 1"),
                ItemAction.parse("console: give %player% diamond 1"));
        assertEquals(new ItemAction(ItemAction.Type.SERVER, "bedwars-1"), ItemAction.parse("server: bedwars-1"));
        assertEquals(new ItemAction(ItemAction.Type.GROUP, "bedwars"), ItemAction.parse("group: bedwars"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, "lobbies"), ItemAction.parse("menu: lobbies"));
        assertEquals(new ItemAction(ItemAction.Type.MESSAGE, "&aHello"), ItemAction.parse("message: &aHello"));
        assertEquals(new ItemAction(ItemAction.Type.ACTIONBAR, "&7Hi %player%"),
                ItemAction.parse("actionbar: &7Hi %player%"));
        assertEquals(new ItemAction(ItemAction.Type.SOUND, "entity.player.levelup"),
                ItemAction.parse("sound: entity.player.levelup"));
        assertEquals(new ItemAction(ItemAction.Type.LOBBY, "premium"), ItemAction.parse("lobby: premium"));
    }

    @Test
    void acceptsAliasesSlashesAndSpacing() {
        assertEquals(new ItemAction(ItemAction.Type.PLAYER, "warp shop"), ItemAction.parse("  Command :  /warp shop "));
        assertEquals(new ItemAction(ItemAction.Type.SERVER, "lobby-2"), ItemAction.parse("connect: lobby-2"));
        assertEquals(new ItemAction(ItemAction.Type.GROUP, "skywars"), ItemAction.parse("play: skywars"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, "lobbies"), ItemAction.parse("menu: Lobby-Selector"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, "languages"), ItemAction.parse("menu: language"));
        assertEquals(new ItemAction(ItemAction.Type.MESSAGE, "a: b"), ItemAction.parse("message: a: b"));
        assertEquals(new ItemAction(ItemAction.Type.SOUND, "minecraft:block.note_block.pling 1 2"),
                ItemAction.parse("sound: minecraft:block.note_block.pling 1 2"));
    }

    @Test
    void rejectsBrokenActions() {
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("spawn"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse(": spawn"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("teleport: spawn"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("player:"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("player: /"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("menu: shop"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("server: two words"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("sound: Not A Key"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("sound: entity.cat.ambient loud"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("sound: entity.cat.ambient 1 5"));
        assertThrows(IllegalArgumentException.class, () -> ItemAction.parse("   "));
    }

    @Test
    void parsesSoundsWithVolumeAndPitch() {
        assertEquals(new SoundSpec("entity.player.levelup", 1.0f, 1.0f), SoundSpec.parse("entity.player.levelup"));
        assertEquals(new SoundSpec("ui.button.click", 0.5f, 1.5f), SoundSpec.parse("UI.Button.Click 0.5 1.5"));
    }

    @Test
    void mapsClicksAndShiftFallsBackToThePlainClick() {
        assertEquals(ItemClick.RIGHT_CLICK, ItemClick.of(false, false));
        assertEquals(ItemClick.SHIFT_LEFT_CLICK, ItemClick.of(true, true));
        assertEquals(Optional.of(ItemClick.SHIFT_RIGHT_CLICK), ItemClick.byId("shift_right_click"));
        assertEquals(Optional.of(ItemClick.LEFT_CLICK), ItemClick.byId("left"));
        assertEquals(Optional.empty(), ItemClick.byId("middle-click"));
        ItemAction spawn = new ItemAction(ItemAction.Type.PLAYER, "spawn");
        ItemAction hub = new ItemAction(ItemAction.Type.PLAYER, "hub");
        CustomItem item = new CustomItem("test", true, 2, "STONE", "", List.of(), false, 0, "",
                Map.of(ItemClick.RIGHT_CLICK, List.of(spawn), ItemClick.SHIFT_LEFT_CLICK, List.of(hub)));
        assertEquals(List.of(spawn), item.actionsFor(ItemClick.RIGHT_CLICK));
        assertEquals(List.of(spawn), item.actionsFor(ItemClick.SHIFT_RIGHT_CLICK));
        assertEquals(List.of(), item.actionsFor(ItemClick.LEFT_CLICK));
        assertEquals(List.of(hub), item.actionsFor(ItemClick.SHIFT_LEFT_CLICK));
    }
}
