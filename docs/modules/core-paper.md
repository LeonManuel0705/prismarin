# Modul `:core-paper` — PrismarinCore (Paper)

## Anforderungen

- Ein Kern-Plugin auf jedem Paper-Backend: Profile, Economy, Chat-Format, Tablist, Sidebar, Menü-Framework, Serververzeichnis.
- Library-Host für alle abhängigen Paper-Module: bettet `:api` + `:shared` ein und lädt Guice/Mongo/Lettuce/Configurate/HikariCP/MariaDB/SQLite über den `libraries:`-Mechanismus der `plugin.yml`.
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
│   └── (ProfileStorage liegt seit 0.2.0 in :shared/storage und wählt JSON, SQLite, MySQL oder Mongo)
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

## Essentials-Modul

Schalter `modules.essentials`, tritt automatisch zurück, wenn `Essentials` (EssentialsX) oder `CMI` installiert ist. `EssentialsCommands.install` registriert die Befehle aus der `plugin.yml` oder entfernt sie, einzeln abschaltbar unter `essentials.commands.<name>` in der `config.yml`. Unsichtbare erscheinen weder in Vorschlägen noch als Ziel für Spieler, die sie nicht sehen; `/tpall` holt nie Unsichtbare. Wer andere ansteuert, braucht `.others`; Hinweise an das Ziel nennen den Auslöser nur, wenn das Ziel ihn sehen kann.

| Schalter | Befehle | Rechte (Default op, außer ping) |
| --- | --- | --- |
| `tpall` | `/tpall` | `prismarin.essentials.tpall` |
| `tphere` | `/tphere <spieler>` | `prismarin.essentials.tphere` |
| `heal` | `/heal [spieler]` (Leben, Hunger, Feuer, Frost, schädliche Effekte) | `prismarin.essentials.heal`, `.others` |
| `feed` | `/feed [spieler]` | `prismarin.essentials.feed`, `.others` |
| `fly` | `/fly [spieler]` | `prismarin.essentials.fly`, `.others` |
| `gamemode` | `/gm <0-3\|s\|c\|a\|sp> [spieler]`, `/gms`, `/gmc`, `/gma`, `/gmsp` | `prismarin.essentials.gamemode`, `.others` |
| `speed` | `/speed <1-10\|reset> [spieler]`, im Flug die Fluggeschwindigkeit | `prismarin.essentials.speed`, `.others` |
| `clearinventory` | `/clearinventory [spieler]`, `/ci` | `prismarin.essentials.clearinventory`, `.others` |
| `invsee` | `/invsee <spieler>` | `prismarin.essentials.invsee`, Bearbeiten `.modify` |
| `enderchest` | `/enderchest [spieler]`, `/ec` | `prismarin.essentials.enderchest`, `.others`, `.modify` |
| `time` | `/day`, `/night [welt]` | `prismarin.essentials.time` |
| `weather` | `/sun`, `/rain`, `/thunder [welt]` | `prismarin.essentials.weather` |
| `ping` | `/ping [spieler]` | `prismarin.essentials.ping`, `.others` (beide Default true) |
| `hat` | `/hat` | `prismarin.essentials.hat` |
| `skull` | `/skull [name]`, fremde Köpfe werden asynchron bei Mojang nachgeschlagen | `prismarin.essentials.skull` |

Das Vanilla-`/clear` bleibt unangetastet, damit Befehlsblöcke und Datenpakete weiter funktionieren. `/fly` übernimmt die Flugerlaubnis von Vanish (`VanishService.releaseFlight`), dadurch nimmt das Aufheben von Vanish einen per `/fly` gesetzten Flug nicht mehr weg. Texte unter `essentials` in den Sprachdateien. Tests: `EssentialsLogicTest`, `EssentialsSettingsTest`, `EssentialsResourcesTest` (beide Sprachen, `plugin.yml`).
