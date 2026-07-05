# Modul `:core-paper` — PrismarinCore (Paper)

## Anforderungen

- Ein Kern-Plugin auf jedem Paper-Backend: Profile, Economy, Chat-Format, Tablist, Sidebar, Menü-Framework, Serververzeichnis.
- Library-Host für alle abhängigen Paper-Module: bettet `:api` + `:shared` ein und lädt Guice/Mongo/Lettuce/Configurate über den `libraries:`-Mechanismus der `plugin.yml`.
- Kein I/O auf dem Main-Thread; Profil-Laden blockiert ausschließlich den Auth-Thread im PreLogin.
- LuckPerms optional (`softdepend`): mit LuckPerms kommen Prefix/Weight/Namensfarbe aus dessen Metadaten, ohne greift ein Zwei-Stufen-Fallback (Admin-Permission vs. Default).

## Architektur

`PrismarinCorePlugin.onEnable` baut einen Guice-Injector aus `CoreModule` (alle Services als `@Singleton`-Provider), registriert Listener und Commands, startet Sidebar-/Tablist-Ticker und den 5-Minuten-Save-Task und veröffentlicht den Injector über `CoreApi.register`. `onDisable` räumt in umgekehrter Reihenfolge auf und flusht alle Online-Profile mit 15 s Timeout. Abhängige Plugins erzeugen Child-Injectoren über `CoreApi.get().injector()`.

Das Jar entsteht ohne Shadow: `:api` und `:shared` werden über die `includedProjects`-Configuration als `zipTree` eingebettet; `plugin.yml` wird beim Build mit der Projektversion expandiert.

## Klassenstruktur

```
net.prismarin.core
├── PrismarinCorePlugin        Lifecycle, Command-Registrierung, Shutdown-Flush
├── CoreApi                    statischer Service-Locator (einziger erlaubter), register/unregister
├── CoreModule                 Guice-Bindings: CoreConfig, MenuSettings, LocaleService, ProfileStorage,
│                              ProfileRepository, ProfileService, EconomyService, MessageBus,
│                              ServerDirectory, MiniMessage, ExecutorService (virtuelle Threads),
│                              RankFormatProvider (LuckPerms-Erkennung)
├── chat
│   └── AsyncChatListener      MiniMessage-Chat nur mit Permission, Renderer pro Viewer-Locale
├── command
│   ├── CoinsCommand           /coins aus dem Profil-Cache
│   ├── EconomyCommand         /economy give|take|set, async Offline-Resolve, Main-Thread-Antworten
│   └── EconomyCommandParser   reine Parse-Logik (sealed Result: Parsed | Invalid)
├── config
│   └── MenuSettings           menu.click-sound/-volume/-pitch aus config.yml, schreibt Defaults nach
├── gui
│   ├── Menu / PagedMenu       Button-Raster 1–6 Reihen; PagedMenu mit Navigationszeile
│   ├── MenuButton / ClickContext / MenuHolder / MenuService (Listener, Klick-Sound, open())
│   └── ItemBuilder / ItemText Item-Fluent-API, Anti-Kursiv-Sanitizing
├── profile
│   └── ConnectionListener     PreLogin-Load (10 s), Join-/Quit-Messages, Unload
├── rank
│   ├── RankFormatProvider     prefix/weight/nameColor
│   ├── DefaultRankFormatProvider   Fallback über prismarin.rank.admin
│   └── LuckPermsRankFormatProvider Meta prefix, Gruppengewicht, Meta-Key "name-color"
├── scoreboard
│   ├── SidebarManager         10-Tick-Ticker, max. 15 Zeilen, NumberFormat.blank()
│   ├── SidebarProvider        title(player) / lines(player) — von Modulen gestellt
│   ├── AnimatedTitle          Gradient-Phasen-Frames für den Brand-Schriftzug
│   └── PlayerBoards           ein Scoreboard pro Spieler
├── server
│   └── YamlServerDirectory    servers.yml -> immutable Liste NetworkServer
├── storage
│   └── ProfileStorage         Auswahl JSON vs. Mongo, hält/schließt MongoClient
├── tablist
│   ├── TablistManager         Header/Footer alle 100 Ticks, Team-Sortierung nach Rang-Gewicht
│   └── TeamSortKeys           "%04d"+Name, 16-Zeichen-Limit
└── task
    └── PeriodicSaveTask       speichert alle Online-Profile alle 5 Minuten asynchron
```

## Events

Registrierte Listener:

| Listener | Events |
| --- | --- |
| `AsyncChatListener` | `AsyncChatEvent` (Paper) — Format `chat.format`, MiniMessage-Parsing nur mit `prismarin.chat.format` |
| `ConnectionListener` | `AsyncPlayerPreLoginEvent` (Profil-Load, Kick bei Fehler), `PlayerJoinEvent` (Locale-Sync, Join-Message, Tablist), `PlayerQuitEvent` (Quit-Message, Cleanup, Profil-Unload) |
| `MenuService` | `InventoryClickEvent`, `InventoryDragEvent`, `InventoryCloseEvent` — nur für `MenuHolder`-Inventare |

Eigene Event-Typen definiert das Modul nicht.

## Commands

| Command | Beschreibung | Permission |
| --- | --- | --- |
| `/coins` | Eigener Coins-Kontostand aus dem Cache | `prismarin.command.coins` |
| `/economy give\|take\|set <Spieler> coins\|gems <Betrag>` | Admin-Verwaltung der Guthaben, funktioniert auch für Offline-Spieler | `prismarin.admin.economy` |

## Permissions

| Permission | Default | Wirkung |
| --- | --- | --- |
| `prismarin.command.coins` | `true` | `/coins` |
| `prismarin.admin.economy` | `op` | `/economy` |
| `prismarin.chat.format` | `op` | Eigene MiniMessage-Tags im Chat |
| `prismarin.rank.admin` | `op` | Admin-Prefix/-Farbe im Fallback-Rangsystem |

## Konfiguration

- `config.yml`: alle `CoreConfig`-Keys (siehe `docs/modules/shared.md`) plus `menu.click-sound` (`minecraft:ui.button.click`), `menu.click-volume` (`0.6`), `menu.click-pitch` (`1.2`).
- `servers.yml`: `servers.<name>.display-name` (MiniMessage), `.host`, `.port`, `.category` (`LOBBY`/`GAME`/`BUILD`). Default-Seed: `lobby` (25566, LOBBY), `creative` (25567, BUILD), `minigame` (25568, GAME).
- `messages_de.yml` / `messages_en.yml`: komplette Locale-Dateien (109 Keys, identische Struktur) inklusive aller `lobby.*`-Keys — die Lobby liest ihre Texte über den Core-`LocaleService`.

## Tests

- `EconomyCommandParserTest` (14): alle Parse-Zweige inkl. Fehler-Keys.
- `ItemTextTest` (7): Anti-Kursiv-Sanitizing, Name-/Lore-Deserialisierung.
- `AnimatedTitleTest` (9): Frame-Erzeugung, Zyklus, Validierung.
- `TeamSortKeysTest` (8): Gewichts-Sortierung, Clamping, 16-Zeichen-Kürzung.

Bukkit-abhängige Klassen (Listener, Manager) sind bewusst dünn gehalten und nicht unit-getestet.

## Erweiterungspunkte

- **`CoreApi` + Child-Injector**: der vorgesehene Weg für neue Paper-Module (siehe `docs/conventions.md`).
- **`SidebarProvider`**: pro Server setzt genau ein Modul den Provider (`SidebarManager.provider(...)`); der Core rendert.
- **`RankFormatProvider`**: weitere Rang-Quellen als neue Implementierung plus Provider-Logik in `CoreModule`.
- **Menü-Framework**: `Menu`/`PagedMenu` subclassen oder komponieren; `MenuService.open` übernimmt Thread-Hop und Klick-Handling.
- **`MessageBus`**: für künftige Cross-Server-Features direkt injizierbar; Kanäle sind frei wählbar.
