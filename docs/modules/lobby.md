# Modul `:lobby-paper` — PrismarinLobby

## Anforderungen

- Lobby-Erlebnis auf dem Lobby-Backend: Spawn-Teleport, geschützte Welt, Hotbar-Items (Navigator, Profil, Spieler-Verstecker), Server-Navigator-Menü, Profil-Menü mit Sprachwechsel, Double-Jump, animierte Sidebar.
- Kein eigener Storage und keine eigenen Bibliotheken: alles kommt zur Laufzeit von PrismarinCore (`depend: [PrismarinCore]`, alle Projekt-Abhängigkeiten `compileOnly`).
- Admins können per Build-Modus die Schutzregeln für sich aufheben und den Spawn neu setzen.

## Architektur

`LobbyPlugin.onEnable` holt den Core-Injector über `CoreApi.get()` und erzeugt einen Child-Injector mit `LobbyModule` (bindet alle Lobby-Services, provided `LocationsFile`). Dadurch teilen sich Core und Lobby dieselben Singletons (`ProfileService`, `LocaleService`, `MenuService`, `SidebarManager`, `ExecutorService`). Die Lobby registriert ihren `LobbySidebarProvider` am Core-`SidebarManager` und meldet ihn bei `onDisable` wieder ab. Serverwechsel laufen als Plugin-Message über den `BungeeCord`-Channel.

Alle Anzeigetexte liegen als `lobby.*`-Keys in den Locale-Dateien von `core-paper` — das Modul bringt keine eigenen messages-Dateien mit.

## Klassenstruktur

```
net.prismarin.lobby
├── LobbyPlugin                Lifecycle über CoreApi-Child-Injector, Sidebar-Registrierung
├── LobbyModule                Guice-Bindings + LocationsFile-Provider
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

## Erweiterungspunkte

- Neue Hotbar-Items: Konstante + Slot in `LobbyItems`, Zweig im `HotbarListener`, Keys in den Core-Locale-Dateien.
- Neue Menüs über das Core-Framework (`Menu`, `PagedMenu`, `MenuService`); `MenuLayout.centeredRowSlots` für symmetrische Layouts.
- `LobbySidebarProvider` ist austauschbar — ein anderes Modul kann einen eigenen `SidebarProvider` registrieren.
- `ServerConnector` ist der einzige Serverwechsel-Pfad; ein späterer Wechsel auf MessageBus-basierte Moves ersetzt nur diese Klasse.
- Lines in the profile head from other plugins: register a `PlayerAttribute` with a `profileLine` through `PrismarinProvider.get().players().attributes()` (see the player section in `core-paper.md`). The lines follow the configured `profile.head` lines; `attribute: <key>` places one attribute, `attributes` places all that are not placed elsewhere. Parsing is tested in `ProfileAttributeLinesTest`.
