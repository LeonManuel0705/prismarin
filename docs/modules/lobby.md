# Modul `:lobby-paper` — PrismarinLobby

## Anforderungen

- Lobby-Erlebnis auf dem Lobby-Backend: Spawn-Teleport, geschützte Welt, Hotbar-Items (Navigator, Profil, Spieler-Verstecker), Server-Navigator-Menü, Profil-Menü mit Sprachwechsel, Double-Jump, animierte Sidebar.
- Kein eigener Storage und keine eigenen Bibliotheken: alles kommt zur Laufzeit von PrismarinCore (`depend: [PrismarinCore]`, alle Projekt-Abhängigkeiten `compileOnly`).
- Admins können per Build-Modus die Schutzregeln für sich aufheben und den Spawn neu setzen.

## Architektur

`LobbyPlugin.onEnable` holt den Core-Injector über `CoreApi.get()` und erzeugt einen Child-Injector mit `LobbyModule` (bindet alle Lobby-Services, provided `LocationsFile`). Dadurch teilen sich Core und Lobby dieselben Singletons (`ProfileService`, `LocaleService`, `MenuService`, `SidebarManager`, `ExecutorService`). Die Lobby registriert ihren `LobbySidebarProvider` mit `SidebarService.LOBBY_PRIORITY` am Core-`SidebarManager` und meldet ihn bei `onDisable` über die `Subscription` wieder ab. Ihre Plugin-API (`LobbyService`) registriert sie in `LobbyApi` und im ServicesManager. Serverwechsel laufen als Plugin-Message über den `BungeeCord`-Channel.

Alle Anzeigetexte liegen als `lobby.*`-Keys in den Locale-Dateien von `core-paper` — das Modul bringt keine eigenen messages-Dateien mit.

## Klassenstruktur

```
net.prismarin.lobby
├── LobbyPlugin                Lifecycle über CoreApi-Child-Injector, Sidebar-Registrierung
├── LobbyModule                Guice-Bindings + LocationsFile-Provider
├── api                        stabile Plugin-API: LobbyService, LobbyApi, LobbyHotbarItem, HotbarClick,
│                              LobbyMenuEntry, LobbyMenu, LobbyInfo, LobbyEnteredEvent, LobbyLeftEvent
├── extension
│   ├── PaperLobbyService      LobbyService-Implementierung
│   ├── LobbyExtensions        registrierte Items und Einträge, Icons und Knöpfe
│   └── EntryRegistry          Ids, Reihenfolge, Fehler einmal loggen (rein)
├── command
│   ├── LobbyBuildCommand      /lobbybuild — Creative + Schutz-Aufhebung togglen
│   └── SetSpawnCommand        /setspawn — Spawn speichern (async Save, Main-Thread-Antwort)
├── config
│   ├── LocationsFile          locations.yml lesen/schreiben (volatile Spawn-Snapshot)
│   └── SpawnPoint             Record world/x/y/z/yaw/pitch
├── item
│   └── LobbyItems             Hotbar-Items mit PersistentDataContainer-Tag (Slots 0/4/8)
├── listener
│   ├── JoinListener           Spawn-Teleport, Adventure-Mode, Heilung, Items, Titel + Sound
│   ├── HotbarListener         Rechtsklick-Aktionen, Verschiebe-/Drop-/Swap-Schutz der Items
│   ├── ProtectionListener     Weltschutz + Void-Teleport (y < void-teleport-y)
│   └── DoubleJumpListener     Flug-Toggle als Sprungimpuls, Partikel + Sound
├── menu
│   ├── LobbyMenus             Navigator- und Profil-Menü (3 Reihen, Glas-Füllung)
│   └── MenuLayout             zentrierte Slot-Berechnung für n Buttons in einer Reihe
├── player
│   ├── BuildModeService       Set aktiver Builder (Permission-geprüft)
│   ├── PlayerHiderService     Alle-verstecken-Toggle mit 3 s Cooldown, Join-Sync
│   └── PlayerLocales          Locale aus Profil-Cache, sonst Client-Locale
├── server
│   └── ServerConnector        "Connect"-Plugin-Message auf Channel BungeeCord
├── sidebar
│   └── LobbySidebarProvider   animierter Titel, Rang, Coins/Gems, Level + XP-Balken, Online-Zahl
├── util
│   ├── CooldownTracker        millisekundengenauer Cooldown pro UUID
│   └── XpProgressBar          ▮/▯-Balken als MiniMessage-String
└── world
    └── LobbyWorlds            Lobby-Welt-Check, Spawn-Location, Wetter-Sperre
```

## Events

| Listener | Events |
| --- | --- |
| `JoinListener` | `PlayerJoinEvent` (Teleport, Gamemode, Items, Willkommens-Titel), `PlayerQuitEvent` (Hider/Buildmode-Cleanup) |
| `HotbarListener` | `PlayerInteractEvent` (Rechtsklick-Aktionen), `InventoryClickEvent`, `InventoryDragEvent`, `PlayerSwapHandItemsEvent`, `PlayerDropItemEvent` (Item-Fixierung) |
| `ProtectionListener` | `BlockBreakEvent`, `BlockPlaceEvent`, `EntityDamageEvent`, `FoodLevelChangeEvent`, `PlayerDropItemEvent`, `EntityPickupItemEvent`, `WeatherChangeEvent`, `ThunderChangeEvent`, `CreatureSpawnEvent` (außer Armor Stands), `BlockIgniteEvent`, `BlockBurnEvent`, `EntityExplodeEvent`, `BlockExplodeEvent`, `PlayerMoveEvent` (Void-Teleport) |
| `DoubleJumpListener` | `PlayerToggleFlightEvent` (Sprungimpuls), `PlayerMoveEvent` (Flight-Reset am Boden) |
| `PlayerHiderService` | `PlayerJoinEvent` (neue Spieler für Versteckende ausblenden) |

## Commands

| Command | Beschreibung | Permission |
| --- | --- | --- |
| `/lobbybuild` (Alias `/build`) | Build-Modus togglen: Creative an/aus, Lobby-Items werden beim Einschalten weggeräumt und beim Ausschalten zurückgegeben (Inventar wird dabei geleert, Rüstung und Hut bleiben), Schutzregeln aufgehoben | `prismarin.lobby.build` |
| `/setspawn` | Aktuelle Position als Lobby-Spawn speichern | `prismarin.lobby.setspawn` |

## Permissions

| Permission | Default | Wirkung |
| --- | --- | --- |
| `prismarin.lobby.build` | `op` | `/lobbybuild` und Wirksamkeit des Build-Modus |
| `prismarin.lobby.setspawn` | `op` | `/setspawn` |

## Konfiguration

`locations.yml` (Seed aus dem Jar, per `/setspawn` überschrieben):

| Key | Default | Bedeutung |
| --- | --- | --- |
| `spawn.world` | `world` | Lobby-Welt; definiert zugleich, wo Schutz/Double-Jump greifen |
| `spawn.x` / `y` / `z` | `0.5` / `4.0` / `0.5` | Spawn-Koordinaten |
| `spawn.yaw` / `pitch` | `0.0` / `0.0` | Blickrichtung |
| `void-teleport-y` | `0.0` | Unterhalb dieser Höhe wird zum Spawn teleportiert |

Anzeige-Keys (`lobby.join.*`, `lobby.item.*`, `lobby.navigator.*`, `lobby.profile.*`, `lobby.hider.*`, `lobby.build.*`, `lobby.setspawn.*`, `lobby.sidebar.*`) liegen in `core-paper/src/main/resources/messages_de.yml` / `messages_en.yml`.

## Tests

- `LocationsFileTest` (3): Seed, Roundtrip, Spawn-Update.
- `MenuLayoutTest` (5): zentrierte Slots mit/ohne Lücken, Grenzfälle, Validierung.
- `CooldownTrackerTest` (6): Erst-Nutzung, Sperrzeit, Restzeit, Ablauf, clear.
- `XpProgressBarTest` (8): Segment-Berechnung inkl. Rändern, MiniMessage-Rendering.
- `EntryRegistryTest` (7): Ids, Reihenfolge, Abmelden, Slot-Prüfung, Fehler-Schlucken der Plugin-API.
- `LobbyModelTest` (3): `LobbyInfo`, Lobby-Events, `LobbyMenu` passt zu den Menü-Aktionen.

## Erweiterungspunkte

- Hotbar-Items, Navigator-, Profil- und Lobby-Auswahl-Einträge anderer Plugins: `LobbyService` (siehe „Lobby API“).
- Neue eingebaute Hotbar-Items: Konstante + Slot in `LobbyItems`, Zweig im `HotbarListener`, Keys in den Core-Locale-Dateien.
- Neue Menüs über das Core-Framework (`Menu`, `PagedMenu`, `MenuService`); `MenuLayout.centeredRowSlots` für symmetrische Layouts.
- `LobbySidebarProvider` läuft mit Priorität 0; ein anderes Plugin registriert über `SidebarService` einen eigenen `SidebarProvider` mit höherer Priorität, etwa nur für seine Welten.
- `ServerConnector` ist der einzige Serverwechsel-Pfad; ein späterer Wechsel auf MessageBus-basierte Moves ersetzt nur diese Klasse.
- Lines in the profile head from other plugins: register a `PlayerAttribute` with a `profileLine` through `PrismarinProvider.get().players().attributes()` (see the player section in `core-paper.md`). The lines follow the configured `profile.head` lines; `attribute: <key>` places one attribute, `attributes` places all that are not placed elsewhere. Parsing is tested in `ProfileAttributeLinesTest`.

## Queue in the navigator

With `features.navigator-queue` (on by default) and the `queue` module on the proxy, group and server buttons in the navigator show `lobby.navigator.queue` ("3 waiting in the queue") while someone waits, and a group without a free server says `lobby.navigator.group-queue-click` instead of `lobby.navigator.group-none`. Clicking a full group or server puts the player into its queue. `QueueBoard` keeps the sizes from the relayed `QueueJoinedEvent`, `QueueLeftEvent` and `QueueSentEvent` and asks the proxy for all queues at most every 15 seconds when the navigator opens (tested in `QueueBoardTest`). Server and group buttons travel through `QueueingConnector`, see [`core-paper.md`](core-paper.md#server-queue-on-backends).

## Lobby API

Game and network plugins extend the lobby through `net.prismarin.lobby.api.LobbyService` instead of shipping their own hotbar or menus. It is available while PrismarinLobby runs: `LobbyApi.service()` (empty otherwise), `LobbyApi.get()` (throws otherwise) and `Bukkit.getServicesManager().load(LobbyService.class)`. Add `PrismarinLobby` to `depend` or `softdepend` in the `plugin.yml`. Everything here runs on the main thread. Registrations return a `Subscription`, call `unsubscribe()` in `onDisable`.

### Hotbar items

```java
LobbyService lobby = LobbyApi.get();
Subscription stats = lobby.addHotbarItem(LobbyHotbarItem.of("bedwars-stats", 6,
        player -> ItemBuilder.of(Material.RED_BED).name(Component.text("BedWars stats")).build(),
        click -> statsMenu.open(click.player())));

Subscription shop = lobby.addHotbarItem(new LobbyHotbarItem() {
    @Override
    public String id() {
        return "cosmetics";
    }

    @Override
    public int slot() {
        return 2;
    }

    @Override
    public ItemStack icon(Player player) {
        return cosmetics.icon(player);
    }

    @Override
    public void click(HotbarClick click) {
        if (click.left() && click.sneaking()) {
            cosmetics.clear(click.player());
        } else {
            cosmetics.open(click.player());
        }
    }

    @Override
    public boolean visible(Player player) {
        return player.hasPermission("cosmetics.use");
    }
});
```

- Ids are lower case `[a-z0-9_-]`, at most 48 characters and unique, slots 0 to 8; `addHotbarItem` throws otherwise.
- The items are handed out with the built in ones whenever a player enters a lobby world, and refreshed for everyone in the lobby when an item is added or removed. `refreshHotbar(player)` rebuilds the hotbar of one player, for example after the icon changed.
- They are protected like the built in items (no moving, dropping or swapping). An added item takes its slot over from a built in item; pick a free slot or move the built in item in the lobby config.
- `click` gets left and right clicks with the sneak state, with the same 200 ms debounce as the built in items.

### Menu entries and built in menus

```java
Subscription arena = lobby.addNavigatorEntry(LobbyMenuEntry.of("arena",
        viewer -> ItemBuilder.of(Material.DIAMOND_SWORD).name(Component.text("Arena")).build(),
        context -> queue.join(context.player())));
Subscription quests = lobby.addProfileEntry(LobbyMenuEntry.of("quests", quests::icon,
        context -> quests.open(context.player())));
Subscription event = lobby.addSelectorEntry(LobbyMenuEntry.of("event-lobby", events::icon,
        context -> events.teleport(context.player())));

lobby.open(player, LobbyMenu.PROFILE);
```

- Navigator entries follow the servers and groups of `servers.yml`, lobby selector entries follow the lobbies, profile entries join the action row of the profile menu. All are placed automatically and sorted by `order()` (default 0), then by registration.
- `visible(viewer)` hides an entry per player, `icon` is called every time the menu opens. The `ClickContext` gives the player, the menu and the `MenuService` (close it with `context.menus().close(player)`).
- `open(player, menu)` opens `NAVIGATOR`, `PROFILE`, `LOBBY_SELECTOR`, `LANGUAGES`, `FRIENDS`, `PARTY`, `CLAN` or `NICK_TOOL` and returns `false` when that feature is switched off.

### Clans in the lobby

- `profile.menu.entries.rank` is `auto` by default, so with two currencies the info row (currencies, level, rank, language) has five entries like the action row below it and both rows line up. Rows with six or eight entries leave the middle slot free (`MenuLayout.centeredRowSlots`) so they stay symmetric.
- The profile menu gets a `clan` entry (white banner) next to friends and party, `profile.menu.entries.clan: auto` by default; `off` hides it, a number pins it. Existing configs get the key added once. It only shows when the proxy has the `clans` module on, its lore shows the own clan with tag and role.
- `menu: clan` opens the clan menu from a custom hotbar item, `LobbyService.open(player, LobbyMenu.CLAN)` from code.
- The clan menu shows the clan with members sorted by role (heads, server, role marker), invited players and the buttons for clan settings, leaving and disbanding (with a confirmation). A member click offers promote, demote, hand over the lead, add as friend and kick, only what the proxy allows for the viewer. Without a clan it lists open invites (left click joins, right click declines) and how to found one.
- `clan` is a new line kind for `profile.head`: the head item then shows the clan tag and name. It is not in the default list.
- The friends menu shows the clan of each friend in the tooltip, the party menu the clan of each member (`clans.show-in` in the proxy's `social.yml`).
- Buttons of switched off commands are gone: requests, jump, personal settings in the friends menu, party settings, warp, disband and member actions in the party menu, settings and disband in the clan menu.

### Worlds, spawns and lobbies

| Method | Answer |
| --- | --- |
| `worlds()`, `isLobbyWorld(world)`, `inLobby(player)` | the lobby worlds of this server (configured worlds, world lobbies and worlds with a spawn) |
| `spawn(world)` | the spawn set with `/setspawn` in that world |
| `spawnFor(player)` | where a join would put the player (lobby distribution, then the default spawn) |
| `sendToSpawn(player)` | teleports there, `false` without a spawn |
| `lobbies()` | the entries of the lobby selector as `LobbyInfo` (id, display name, `WORLD` or `SERVER`, world or server name, permission, soft player limit, `premium()`) |
| `lobbyServers()` | the servers of the group `lobby` from `servers.yml` |
| `travel(player, lobbyId)` | sends the player there with the checks of the selector (permission, reachable), `false` for an unknown id |

```java
if (lobby.inLobby(player)) {
    lobby.sendToSpawn(player);
}
lobby.lobbies().stream().filter(LobbyInfo::premium).findFirst()
        .ifPresent(premium -> lobby.travel(player, premium.id()));
```

### Events

On the PRISMARIN `EventBus`, on the main thread:

| Event | When |
| --- | --- |
| `LobbyEnteredEvent(player, world, previousWorld, cause)` | `JOIN`: the player joined into a lobby world (after the spawn teleport and the hotbar). `WORLD_CHANGE`: he changed into a lobby world, also from another lobby world |
| `LobbyLeftEvent(player, world, nextWorld, cause)` | `WORLD_CHANGE`: he left a lobby world. `QUIT`: he quit while in a lobby world |

```java
PrismarinProvider.get().events().subscribe(LobbyEnteredEvent.class, event -> {
    Player player = Bukkit.getPlayer(event.player());
    if (player != null) {
        pets.spawn(player);
    }
});
```

Classes: `PaperLobbyService`, `LobbyExtensions` (registries, icons and buttons), `EntryRegistry` and `HotbarSlots` (pure, tested in `EntryRegistryTest`).

## Leaderboards

Feature `leaderboards` in the lobby `config.yml`, needs `modules.stats` of PrismarinCore. Without either there is no `/leaderboard` and no board is shown. Boards are Paper `TextDisplay` entities, no hologram plugin is needed.

| Command | Effect | Permission |
| --- | --- | --- |
| `/leaderboard create <id> <stat> [period] [size]` | places a board where you stand; period `alltime` (default), `weekly` or `monthly` (only enabled periods), size 1 to 20 (default 10), in any order | `prismarin.lobby.leaderboard` (`op`) |
| `/leaderboard move <id>` | moves a board to you | same |
| `/leaderboard remove <id>` | removes a board | same |
| `/leaderboard list` | every board with stat, period, size and position | same |

Aliases `/leaderboards` and `/lb`. Ids match `[a-z0-9][a-z0-9-]{0,31}`. Tab completion offers the actions, the ids, the stat keys of `stats.yml`, plugin definitions, the shared catalog and the featured stats of every game, the enabled periods and sizes.

### How a board is shown

- One entity per board and language in use: each player sees only the entity of his language (`setVisibleByDefault(false)`, `showEntity`), so stat names, periods and number formats follow the player's language. Entities of languages nobody uses anymore are removed.
- Entities are not persistent. They are spawned when the chunk loads (`ChunkLoadEvent`), when a player joins and on every refresh, and removed on disable.
- Every `refresh-seconds` the board asks `StatsService.top(stat, period, size)` (served from the core cache) and updates the text on the main thread. Stats of other plugins work the same: pushed and placeholder stats come from the store, a pull source with its own top lists answers the list itself, the values of a source without top lists are mirrored into the store whenever they are read (players the source was read for at least once).
- Names come from the stats service: a nicked player who is online and visible appears with his nick, everyone else with the real name, vanished players never with a nick.
- Lines: `lobby.leaderboard.title` (`%stat%`, `%period%`), `lobby.leaderboard.period`, an empty line, then one `lobby.leaderboard.line` per place (`%rank%`, `%player%`, `%value%`, `%stat%`, `%period%`) or `lobby.leaderboard.empty` for places nobody holds yet. `%rank%` is `lobby.leaderboard.rank.1` to `.3` or `.other`. A board's own `title` or `line` in `leaderboards.yml` replaces the text for that board (a text or a message key).

### `leaderboards.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `refresh-seconds` | `30` | 5 to 3600 |
| `display.billboard` | `center` | `center`, `fixed` (faces the direction it was placed in), `vertical`, `horizontal` |
| `display.background` | `#80101826` | `#AARRGGBB`, `#RRGGBB`, `default` or `none` |
| `display.shadow`, `display.line-width`, `display.scale` | `true`, `250`, `1.0` | text shadow, wrap width in pixels, size |
| `boards.<id>` | written by the command | `stat`, `period`, `size`, `world`, `x`, `y`, `z`, `yaw`, `title`, `line` |

Broken boards are skipped with a warning. The file is saved off the main thread. Classes: `leaderboard/LeaderboardDisplays`, `LeaderboardsFile`, `LeaderboardBoard`, `LeaderboardArguments`, `LeaderboardCommand`. Tests: `LeaderboardsFileTest`, `LeaderboardArgumentsTest`.

## Profile entries for stats and daily rewards

`profile/ProfileEntries` registers two entries in the profile menu through `LobbyExtensions` when `features.profile-menu` is on:

- `prismarin-stats` (only with `modules.stats` and `profile.enabled` in `stats.yml`): a book with up to `profile.limit` stats marked `in-profile`, click runs `/stats`.
- `prismarin-daily` (only with `modules.daily-rewards` and `profile-entry` in `daily-rewards.yml`): a glowing chest while today's reward is open, else an ender chest with the time until the next one, the streak, the best streak and what the next day gives. Click claims and reopens the profile.

Texts `stats.menu.*` and `daily.menu.*` in the PrismarinCore message files.

## JoinMe in the navigator

`joinme/JoinMeEntries` adds the navigator entry `prismarin-joinme` (a firework rocket, glowing while JoinMes are open, with their number) through `LobbyExtensions` when the lobby feature `navigator` is on, PrismarinCore has `modules.joinme` and `lobby.navigator-entry` is true in the server's `joinme.yml`. A click asks the proxy for the open JoinMes the player may see and opens a paged menu with one head per JoinMe (creator, game, server, players, state, time left); clicking a head runs the hidden `/joinme join <id>`. A nicked creator shows a plain head unless the viewer has `prismarin.nick.realname`, so the real skin stays hidden. Texts `joinme.menu.*` in the PrismarinCore message files.


## Cosmetics in the lobby

The cosmetics menu of PrismarinCore (see [`core-paper.md`](core-paper.md#cosmetics)) is reached through its own hotbar item: `items.cosmetics` (slot 5, `CHEST`, texts `lobby.item.cosmetics.name` and `.lore`), shown while the lobby feature `cosmetics` is on and PrismarinCore runs `modules.cosmetics`. Right click opens the same menu as `/cosmetics`. Custom items can open it with `player: cosmetics`. With `lobby.profile-entry: true` in `cosmetics.yml` there is also an entry `prismarin-cosmetics` in the profile menu (off by default, so the profile rows stay balanced), registered by `cosmetics/CosmeticEntries` through `LobbyExtensions`; the menu then has a back button to the profile. Hats and particles in lobby worlds follow `cosmetics.yml`; the player hider and the silent lobby hide the particles of hidden players, because both use `hidePlayer`.
