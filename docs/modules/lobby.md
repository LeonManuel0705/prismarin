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
| `/lobbybuild` | Build-Modus togglen (Creative an/aus, Schutzregeln aufgehoben) | `prismarin.lobby.build` |
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
- `open(player, menu)` opens `NAVIGATOR`, `PROFILE`, `LOBBY_SELECTOR`, `LANGUAGES`, `FRIENDS`, `PARTY` or `NICK_TOOL` and returns `false` when that feature is switched off.

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
