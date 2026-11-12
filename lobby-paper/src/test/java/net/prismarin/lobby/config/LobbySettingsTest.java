package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.action.ItemClick;
import net.prismarin.shared.status.LobbySelector.Strategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LobbySettingsTest {

    @TempDir
    Path directory;

    @Test
    void freshFileEnablesEverything() throws IOException {
        Path file = directory.resolve("config.yml");
        LobbySettings settings = LobbySettings.load(file);
        LobbySettings.ALL.forEach(feature -> assertTrue(settings.enabled(feature)));
        assertTrue(Files.readString(file).contains("double-jump: true"));
    }

    @Test
    void switchedOffFeaturesStayOffAndMissingOnesAreAdded() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "features:\n  double-jump: false\n  sidebar: false\n");
        LobbySettings settings = LobbySettings.load(file);
        assertFalse(settings.enabled(LobbySettings.DOUBLE_JUMP));
        assertFalse(settings.enabled(LobbySettings.SIDEBAR));
        assertTrue(settings.enabled(LobbySettings.NAVIGATOR));
        assertTrue(Files.readString(file).contains("navigator: true"));
    }

    @Test
    void freshFileSeedsWorldsLobbiesItemsAndTheExample() {
        LobbySettings settings = LobbySettings.load(directory.resolve("config.yml"), "hub");
        assertEquals(List.of("hub"), settings.worlds());
        assertEquals(Strategy.LEAST_PLAYERS, settings.distribution());
        assertFalse(settings.premiumAutoJoin());
        assertEquals(2, settings.lobbies().size());
        WorldLobby main = settings.lobby("main").orElseThrow();
        assertTrue(main.enabled());
        assertEquals("hub", main.world());
        assertFalse(main.premium());
        WorldLobby premium = settings.lobby("premium").orElseThrow();
        assertFalse(premium.enabled());
        assertEquals(LobbySettings.PREMIUM_PERMISSION, premium.permission());
        assertEquals(0, settings.item(LobbySettings.NAVIGATOR_ITEM).slot());
        assertEquals(1, settings.item(LobbySettings.LOBBY_SELECTOR_ITEM).slot());
        assertEquals(4, settings.item(LobbySettings.PROFILE_ITEM).slot());
        assertEquals(7, settings.item(LobbySettings.PREMIUM_ITEM).slot());
        assertEquals("TNT", settings.item(LobbySettings.PREMIUM_ITEM).material());
        assertEquals(8, settings.item(LobbySettings.HIDER_ITEM).slot());
        assertEquals("GRAY_DYE", settings.item(LobbySettings.HIDER_ITEM).hiddenMaterial());
        CustomItem example = settings.customItems().getFirst();
        assertEquals("example", example.id());
        assertFalse(example.enabled());
        assertEquals(ItemAction.Type.SOUND, example.actionsFor(ItemClick.RIGHT_CLICK).getFirst().type());
        assertEquals(new ItemAction(ItemAction.Type.PLAYER, "coins"),
                example.actionsFor(ItemClick.LEFT_CLICK).getFirst());
        assertEquals(ProfileLayout.DEFAULT, settings.profile());
        assertEquals(List.of(), settings.warnings());
    }

    @Test
    void writesHeaderAndCommentsAndStaysStableOnReload() throws IOException {
        Path file = directory.resolve("config.yml");
        LobbySettings.load(file, "world");
        String first = Files.readString(file);
        assertTrue(first.startsWith("# PRISMARIN Lobby settings."));
        assertTrue(first.contains("# Every text accepts MiniMessage tags"));
        assertTrue(first.contains("  # How joining players are spread: least-players, fill-first or random."));
        assertTrue(first.contains("# Your own hotbar items."));
        LobbySettings.load(file, "world");
        assertEquals(first, Files.readString(file));
    }

    @Test
    void readsCustomItemsAndReportsBrokenParts() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                custom-items:
                  shop:
                    slot: 3
                    material: CHEST
                    name: "&aShop"
                    lore: "&7One line"
                    glow: true
                    custom-model-data: 7
                    permission: shop.use
                    actions:
                      right-click:
                        - "player: /shop"
                        - "teleport: spawn"
                      left-click: "console: give %player% diamond 1"
                      middle-click:
                        - "message: hi"
                  simple:
                    slot: 5
                    actions:
                      - "menu: lobby-selector"
                  broken:
                    slot: 12
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(2, settings.customItems().size());
        CustomItem shop = settings.customItems().getFirst();
        assertTrue(shop.enabled());
        assertEquals(List.of("&7One line"), shop.lore());
        assertEquals(7, shop.customModelData());
        assertEquals("shop.use", shop.permission());
        assertEquals(List.of(new ItemAction(ItemAction.Type.PLAYER, "shop")), shop.actionsFor(ItemClick.RIGHT_CLICK));
        assertEquals(List.of(new ItemAction(ItemAction.Type.PLAYER, "shop")),
                shop.actionsFor(ItemClick.SHIFT_RIGHT_CLICK));
        assertEquals(List.of(new ItemAction(ItemAction.Type.CONSOLE, "give %player% diamond 1")),
                shop.actionsFor(ItemClick.LEFT_CLICK));
        CustomItem simple = settings.customItems().get(1);
        assertEquals(List.of(new ItemAction(ItemAction.Type.MENU, ItemAction.MENU_LOBBIES)),
                simple.actionsFor(ItemClick.RIGHT_CLICK));
        assertEquals(List.of(), simple.actionsFor(ItemClick.LEFT_CLICK));
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.contains("teleport")));
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.contains("middle-click")));
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.startsWith("custom-items.broken.slot")));
    }

    @Test
    void readsLobbiesDistributionAndProfileLayout() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                worlds:
                  - hub
                  - hub
                  - parkour
                lobbies:
                  distribution: fill-first
                  premium-auto-join: true
                  worlds:
                    one:
                      world: lobby1
                      soft-max-players: 40
                    vip:
                      world: vip
                      permission: rank.vip
                profile:
                  head:
                    - "currency: gems"
                    - rank
                    - empty
                    - "text: &7Playtime %statistic_hours_played%"
                    - "nonsense"
                  menu:
                    rows: 4
                    head-slot: 4
                    entry-row: 2
                    back-slot: 27
                    close-slot: 35
                    language-click: menu
                    entries:
                      rank: 10
                      currencies: auto
                      level: off
                      language: auto
                      shoes: auto
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(List.of("hub", "parkour"), settings.worlds());
        assertEquals(Strategy.FILL_FIRST, settings.distribution());
        assertTrue(settings.premiumAutoJoin());
        assertEquals(List.of("one", "vip"), settings.lobbies().stream().map(WorldLobby::id).toList());
        assertEquals(40, settings.lobbies().getFirst().softMaxPlayers());
        assertTrue(settings.lobbies().get(1).premium());
        ProfileLayout profile = settings.profile();
        assertEquals(List.of(
                new ProfileLayout.Line(ProfileLayout.Line.Kind.CURRENCY, "gems"),
                new ProfileLayout.Line(ProfileLayout.Line.Kind.RANK, ""),
                new ProfileLayout.Line(ProfileLayout.Line.Kind.EMPTY, ""),
                new ProfileLayout.Line(ProfileLayout.Line.Kind.TEXT, "&7Playtime %statistic_hours_played%")),
                profile.head());
        assertEquals(4, profile.rows());
        assertEquals(2, profile.entryRow());
        assertEquals(ProfileLayout.LanguageClick.MENU, profile.languageClick());
        assertEquals(List.of(
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.RANK, 10),
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.CURRENCIES, -1),
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.LANGUAGE, -1)), profile.entries());
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.contains("nonsense")));
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.contains("shoes")));
    }

    @Test
    void fallsBackOnInvalidValues() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                lobbies:
                  distribution: fullest
                items:
                  navigator:
                    slot: 11
                profile:
                  menu:
                    rows: 9
                    close-slot: 99
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(Strategy.LEAST_PLAYERS, settings.distribution());
        assertEquals(0, settings.item(LobbySettings.NAVIGATOR_ITEM).slot());
        assertEquals(5, settings.profile().rows());
        assertEquals(44, settings.profile().closeSlot());
        assertEquals(4, settings.warnings().size());
    }

    @Test
    void warnsAboutItemsSharingASlot() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                custom-items:
                  clash:
                    slot: 8
                    actions:
                      - "menu: profile"
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertTrue(settings.warnings().stream()
                .anyMatch(warning -> warning.contains("slot 8") && warning.contains("custom-items.clash")));
    }

    @Test
    void readsUnquotedListEntriesAndFlagsBrokenOnes() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                custom-items:
                  plain:
                    slot: 3
                    actions:
                      right-click:
                        - player: spawn
                        - message: &aHello
                profile:
                  head:
                    - currency: gems
                    - level
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(List.of(new ItemAction(ItemAction.Type.PLAYER, "spawn")),
                settings.customItems().getFirst().actionsFor(ItemClick.RIGHT_CLICK));
        assertEquals(List.of(new ProfileLayout.Line(ProfileLayout.Line.Kind.CURRENCY, "gems"),
                new ProfileLayout.Line(ProfileLayout.Line.Kind.LEVEL, "")), settings.profile().head());
        assertTrue(settings.warnings().stream()
                .anyMatch(warning -> warning.startsWith("custom-items.plain.actions.right-click entry 2")));
    }

    @Test
    void theCosmeticsItemHasItsOwnSlot() throws IOException {
        LobbySettings settings = LobbySettings.load(directory.resolve("config.yml"));

        HotbarItem cosmetics = settings.item(LobbySettings.COSMETICS_ITEM);
        assertTrue(cosmetics.enabled());
        assertEquals(5, cosmetics.slot());
        assertEquals("CHEST", cosmetics.material());
        assertTrue(settings.enabled(LobbySettings.COSMETICS));
        assertTrue(settings.warnings().isEmpty(), settings.warnings().toString());
        assertTrue(Files.readString(directory.resolve("config.yml")).contains("# Opens the cosmetics menu"));
    }
}
