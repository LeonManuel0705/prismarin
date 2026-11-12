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
├── CoreApi                    statischer Service-Locator (einziger erlaubter), sidebar(), tablist(), chat(), menus()
├── PaperApis                  registriert Vanish, Staff-Modus, Level, Sidebar, Tablist, Chat und Menüs als API
├── api                        stabile Paper-API: SidebarService/SidebarProvider, TablistService/TablistContributor,
│                              ChatService/ChatDecorator
├── CoreModule                 Guice-Bindings: CoreConfig, MenuSettings, LocaleService, ProfileStorage,
│                              ProfileRepository, ProfileService, EconomyService, MessageBus,
│                              ServerDirectory, MiniMessage, ExecutorService (virtuelle Threads),
│                              RankFormatProvider (LuckPerms-Erkennung)
├── chat
│   ├── AsyncChatListener      MiniMessage-Chat nur mit Permission, Renderer pro Viewer-Locale
│   └── ChatDecorations        ChatService: Präfixe, Suffixe, Nachricht, Platzhalter anderer Plugins
├── command
│   ├── CoinsCommand           /coins aus dem Profil-Cache
│   ├── EconomyCommand         /economy give|take|set, async Offline-Resolve, Main-Thread-Antworten
│   └── EconomyCommandParser   reine Parse-Logik (sealed Result: Parsed | Invalid)
├── config
│   └── MenuSettings           menu.click-sound/-volume/-pitch aus config.yml, schreibt Defaults nach
├── gui
│   ├── Menu / PagedMenu       Button-Raster 1–6 Reihen; PagedMenu mit Navigationszeile
│   ├── MenuButton / ClickContext / MenuHolder / MenuService (Listener, Klick-Sound, open/refresh/close)
│   ├── MenuStyle              einheitlicher Look: Glas-Füllung, Akzent, Zurück- und Schließen-Knopf
│   └── ItemBuilder / ItemText Item-Fluent-API, Anti-Kursiv-Sanitizing
├── display
│   ├── PriorityRegistry       Registrierungen mit Priorität, Fehler werden einmal geloggt
│   └── DisplayParts           Präfix- und Suffix-Teile, Zusatzzeilen, Platzhalter zusammenführen
├── level
│   └── PaperLevels            LevelService, solange systems.levels an ist
├── profile
│   └── ConnectionListener     PreLogin-Load (10 s), Join-/Quit-Messages, Unload
├── rank
│   ├── RankFormatProvider     prefix/weight/nameColor
│   ├── DefaultRankFormatProvider   Fallback über prismarin.rank.admin
│   └── LuckPermsRankFormatProvider Meta prefix, Gruppengewicht, Meta-Key "name-color"
├── scoreboard
│   ├── SidebarManager         SidebarService, 10-Tick-Ticker, max. 15 Zeilen, NumberFormat.blank()
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
- `PriorityRegistryTest` (9): Priorität, Reihenfolge bei Gleichstand, Abmelden, Fehler anderer Plugins.
- `DisplayPartsTest` (6): Präfix- und Suffix-Teile wie bisher, Zusatzzeilen, Chat-Platzhalter.
- `VanishRulesTest` (6): Sichtbarkeit nach Level und Recht, Level aus Permissions.

Bukkit-abhängige Klassen (Listener, Manager) sind bewusst dünn gehalten und nicht unit-getestet.

## Erweiterungspunkte

- **`CoreApi` + Child-Injector**: der vorgesehene Weg für neue Paper-Module (siehe `docs/conventions.md`).
- **`SidebarProvider`**: beliebig viele Plugins registrieren Provider mit Priorität (`SidebarService.register`); pro Spieler gewinnt der höchste passende, der Core rendert (siehe „Plugin APIs on Paper“).
- **`TablistContributor` und `ChatDecorator`**: Zeilen und Namens-Dekorationen in Tablist und Chat, ohne den Core zu ersetzen.
- **`RankFormatProvider`**: weitere Rang-Quellen als neue Implementierung plus Provider-Logik in `CoreModule`.
- **Menü-Framework**: `Menu`/`PagedMenu` subclassen oder komponieren; `MenuService.open` übernimmt Thread-Hop und Klick-Handling, `MenuStyle` liefert den einheitlichen Look.
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

## Plugin APIs on Paper

Everything PRISMARIN shows or switches on a backend can be used by other plugins without replacing it. Plugins declare `depend: [PrismarinCore]` (or `softdepend` and check `PrismarinProvider.isAvailable()` / `CoreApi.isAvailable()`). The packages `net.prismarin.api.*`, `net.prismarin.core.api` and `net.prismarin.core.gui` are the stable API; all other `net.prismarin.core.*` packages are internal and may change.

| Feature | Type | Entry point | Empty or missing when |
| --- | --- | --- | --- |
| Vanish | `net.prismarin.api.vanish.VanishService` | `PrismarinProvider.get().vanish()` | module `vanish` is off |
| Staff mode | `net.prismarin.api.staff.StaffModeService` | `PrismarinProvider.get().staffMode()` | staff mode is off |
| Levels | `net.prismarin.api.level.LevelService` | `PrismarinProvider.get().levels()` | `systems.levels: false` |
| Sidebar | `net.prismarin.core.api.SidebarService` | `CoreApi.get().sidebar()` | module `sidebar` is off |
| Tab list | `net.prismarin.core.api.TablistService` | `CoreApi.get().tablist()` | module `tablist` is off |
| Chat line | `net.prismarin.core.api.ChatService` | `CoreApi.get().chat()` | module `chat` is off |
| Menus | `net.prismarin.core.gui.MenuService`, `MenuStyle` | `CoreApi.get().menus()`, `CoreApi.get().menuStyle()` | never |
| Lobby | `net.prismarin.lobby.api.LobbyService` | `LobbyApi.service()` (see `lobby.md`) | PrismarinLobby is not installed |

Every type is also registered in the Bukkit `ServicesManager` while it is available, for example `Bukkit.getServicesManager().load(SidebarService.class)`. Registrations return a `Subscription`; call `unsubscribe()` in `onDisable`. A provider, contributor or decorator that throws is skipped and logged once, it never breaks the PRISMARIN output. Without registrations everything looks and behaves exactly as before.

### Vanish

```java
Prismarin prismarin = PrismarinProvider.get();
boolean hidden = prismarin.vanish().map(vanish -> !vanish.canSee(viewer.getUniqueId(), target.getUniqueId()))
        .orElse(false);
prismarin.vanish().ifPresent(vanish -> vanish.vanish(spectator.getUniqueId()));
prismarin.events().subscribe(VanishChangedEvent.class, event -> spectators.update(event.player(), event.vanished()));
```

| Method | Meaning |
| --- | --- |
| `vanished(uuid)`, `vanishedPlayers()` | vanish state of players on this server, any thread |
| `level(uuid)` | the level a vanished player hides at, for a visible online player the level he would vanish at (`prismarin.vanish.level.<n>`, at least 1), 0 when offline |
| `canSee(viewer, target)` | the player himself, or `prismarin.vanish.see` plus a level at least as high as the target's. A `null` viewer (console, everyone) never sees vanished players |
| `vanish(uuid)`, `reveal(uuid)`, `toggle(uuid)` | main thread only, same effect as `/vanish` (hidden, silent, flight, remembered across server switches, the proxy is told). `vanish` and `reveal` return `false` for offline players and when nothing changed, `toggle` returns the new state |

`VanishChangedEvent(player, vanished, level, cause)` fires on the main thread for every real change with the cause `COMMAND`, `STAFF_MODE`, `PLUGIN` or `JOIN` (vanish restored after a server switch). A vanished player who quits fires no event.

### Staff mode

```java
prismarin.staffMode().ifPresent(staff -> {
    if (!staff.active(player.getUniqueId())) {
        staff.enter(player.getUniqueId());
    }
});
prismarin.events().subscribe(StaffModeChangedEvent.class, event -> hud.staff(event.player(), event.enabled()));
```

`active(uuid)` reads the state, `enter`, `leave` and `toggle` switch it on the main thread exactly like `/mod` (nick and or vanish as configured, stays on after a server switch). `enter` needs `prismarin.staff.mode` and returns `false` without it, for offline players and when already active. `nickIncluded()` and `vanishIncluded()` tell what the staff mode does on this server. `StaffModeChangedEvent(player, enabled, nicked, vanished)` fires on the main thread.

### Levels

The `LevelService` described in `api.md` is registered on Paper as well. `/level` runs through the same profile methods, so `XpGainedEvent` and `LevelChangedEvent` (reason `command`) fire for it too.

```java
prismarin.levels().ifPresent(levels -> levels.addXp(player.getUniqueId(), 25, "skywars-kill"));
```

### Sidebar

Several plugins can offer a sidebar. For each player the provider with the highest priority whose `appliesTo(player)` is true is shown, ties go to the one registered first. The lobby registers its sidebar with `SidebarService.LOBBY_PRIORITY` (0), so a game plugin uses a higher priority for its worlds and the lobby sidebar stays everywhere else.

```java
Subscription board = CoreApi.get().sidebar().map(sidebar -> sidebar.register(new SidebarProvider() {
    @Override
    public boolean appliesTo(Player player) {
        return arenas.playing(player);
    }

    @Override
    public Component title(Player player) {
        return Component.text("BedWars", NamedTextColor.RED);
    }

    @Override
    public List<Component> lines(Player player) {
        return arenas.scoreboard(player);
    }
}, 100)).orElse(null);
```

- Updated every 10 ticks on the main thread, at most 15 lines, `refresh(player)` updates at once, `active(player)` returns the chosen provider.
- `title` may return `null` for the default title (`sidebar.title`). An empty line list hides the sidebar for that player.
- `appliesTo`, `title` and `lines` run on the main thread often, answer from memory.

### Tab list

A `TablistContributor` adds lines below the PRISMARIN header and footer and decorations around the names. Name decorations are part of the scoreboard team, so they show in the tab list and above the head, and they may differ per viewer.

```java
Subscription tab = CoreApi.get().tablist().map(tablist -> tablist.register(new TablistContributor() {
    @Override
    public List<Component> footer(Player viewer) {
        return List.of(Component.text("Season 3 ends on Friday", NamedTextColor.GRAY));
    }

    @Override
    public Component suffix(Player viewer, Player target) {
        return clans.tag(target.getUniqueId());
    }
}, 10)).orElse(null);
```

- Header lines follow `tablist.header`, footer lines follow `tablist.footer`, in priority order.
- Prefixes go before the rank prefix, suffixes after the rank suffix, each separated by a space; empty components are skipped.
- Refreshed every 100 ticks and when a player joins; `refresh()` renders at once (main thread).

### Chat line

A `ChatDecorator` adds badges before the rank, text after the name, rewrites the message per viewer or fills extra placeholders that operators can put into `chat.line` of the language files.

```java
Subscription chatHook = CoreApi.get().chat().map(chat -> chat.register(new ChatDecorator() {
    @Override
    public Component prefix(Player source, Audience viewer) {
        return teams.badge(source.getUniqueId());
    }

    @Override
    public Component message(Player source, Audience viewer, Component message) {
        return viewer instanceof Player target ? mentions.highlight(message, target.getName()) : message;
    }

    @Override
    public Map<String, Component> placeholders(Player source, Audience viewer) {
        return Map.of("team", teams.name(source.getUniqueId()));
    }
}, 0)).orElse(null);
```

```yaml
chat:
  line: "%prefix%%name%%suffix% <dark_gray>[%team%]</dark_gray> <dark_gray>»</dark_gray> %message%"
```

- Decorators run on the async chat thread once per viewer: never touch worlds or entities there and answer from memory.
- Placeholder keys are lower case `[a-z0-9_-]`; `prefix`, `name`, `suffix`, `message` and the brand tags are reserved. With several decorators the higher priority wins a key. Unknown `%key%` stay for PlaceholderAPI.
- Message rewrites run one after another in priority order.

### Menus

`net.prismarin.core.gui` is the menu toolkit PRISMARIN uses itself, so menus of other plugins look and sound the same.

| Type | Use |
| --- | --- |
| `Menu(title, rows)` | 1 to 6 rows; `button(slot, button)`, `fill(pane)` for all empty slots, `onClose(player -> ...)` runs when the player closes the menu or opens another inventory, not when the same menu is opened again |
| `PagedMenu(title, rows, items, (context, item) -> ..., previous, next)` | pages through a list with arrows in the last row |
| `MenuButton` | `MenuButton.of(icon, player -> ...)`, `MenuButton.display(icon)` without action, or `new MenuButton(icon, context -> ...)` |
| `ClickContext` | `player()`, `slot()`, `clickType()`, `menu()`, `menus()` |
| `MenuService` | `open(player, menu)` from any thread (opens on the next tick, subclasses build in `prepare(viewer)`), `refresh(player)`, `close(player)`, `openMenu(player)` |
| `MenuStyle` | the PRISMARIN look: `fill(menu)` black panes, `accent(menu, slots)` cyan panes, `back(locale, action)` and `close(locale)` with the texts `menu.back` and `menu.close` |
| `ItemBuilder` | `of(material)`, `name`, `lore`, `amount`, `glow`, `skullOwner`, `headTexture`, `build()`; names never turn italic |

Every click in a PRISMARIN menu is cancelled, items cannot be taken out, clicks play `menu.click-sound`.

```java
MenuStyle style = CoreApi.get().menuStyle();
Locale language = PrismarinProvider.get().players().locale(player.getUniqueId());
Menu menu = new Menu(Component.text("Choose a kit"), 3);
style.fill(menu);
menu.button(13, MenuButton.of(ItemBuilder.of(Material.IRON_SWORD).name(Component.text("Warrior")).build(),
        clicked -> kits.select(clicked, "warrior")));
menu.button(26, style.close(language));
menu.onClose(kits::save);
CoreApi.get().menus().open(player, menu);
```

Classes: `PaperApis` (registration), `PaperVanishService`, `PaperStaffModeService`, `PaperLevels`, `ChatDecorations`, `SidebarManager`, `TablistManager`; pure and tested: `PriorityRegistry` (`PriorityRegistryTest`), `DisplayParts` (`DisplayPartsTest`), `VanishRules` (`VanishRulesTest`).

## Friends and parties for backend plugins

`PrismarinCore` registers `net.prismarin.api.social.FriendService` and `PartyService` in the Bukkit `ServicesManager` and behind `PrismarinProvider.get().friends()` / `.parties()`. Everything works without Redis.

- Parties: the proxy pushes a `party-sync` message for every change and when a member joins this server. `BackendPartyService.partyOf` and `party(id)` read this mirror without waiting, it holds every party with at least one member online on this server, including the roles of all members. `lookup(player)` asks the proxy through an online player when the mirror does not know the player.
- Friends: with `storage.mode` `MONGO` or `MYSQL` the friend lists are read from the shared document store (collections `friends` and `presence`). With `LOCAL` or `SQLITE` the server asks the proxy through an online player instead, the list then holds at most 250 friends.
- Actions (`invite`, `kick`, `promote`, `sendRequest`, ...) go to the proxy as the acting player, who has to be online on this server, otherwise the result is `UNAVAILABLE`. The proxy checks the role and answers with a `SocialResult`; without an answer within four seconds the result is `UNAVAILABLE`.
- The same party events as on the proxy fire on the main thread for parties with a member or invitee on this server, the first sync after a server switch fires none.

```java
PartyService parties = Bukkit.getServicesManager().load(PartyService.class);
parties.partyOf(player.getUniqueId()).ifPresent(party -> {
    boolean moderator = party.role(player.getUniqueId()).orElse(PartyRole.MEMBER).atLeast(PartyRole.MODERATOR);
    player.sendMessage(party.size() + " players, you may kick: " + moderator);
});
parties.invite(player.getUniqueId(), target.getUniqueId()).thenAccept(result ->
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("Invite: " + result.id())));

FriendService friends = PrismarinProvider.get().friends().orElseThrow();
friends.areFriends(player.getUniqueId(), target.getUniqueId()).thenAccept(together -> { });
prismarin.events().subscribe(PartyLeftEvent.class, event -> teams.remove(event.player()));
```

Futures complete off the main thread, switch back before touching Bukkit. Classes: `BackendFriendService`, `BackendPartyService`, `BackendSocial` (registration), `PartyMirror` (pure, tested in `PartyMirrorTest`), `SocialClient` (requests, queries, quit cleanup). The lobby party menu shows roles, a party settings menu and only the buttons the proxy allows (`SocialView.Permissions`).

## Clans on backends

`PrismarinCore` registers `net.prismarin.api.social.ClanService` in the Bukkit `ServicesManager` and behind `PrismarinProvider.get().clans()`. It is `BackendClanService`:

- `clanOf(player)` and `clan(id)` read the `ClanMirror` without waiting. The proxy fills it with a `clan-sync` message for every change and when a member reaches this server, so it knows the clan of every player online here, with all members and their roles. `rights()` and the tag format come with the same message (and with every `social-view`).
- `lookup`, `byId`, `find`, `list` and every write call (`createClan`, `addMember`, `setData`, `broadcast`, `invite`, `chat`, ...) go to the proxy as a `network-request` through any online player, or over Redis when nobody is online. Without a route the result is `UNAVAILABLE` (or an empty `Optional`).
- Every clan event of the proxy arrives here as well (relayed as `network-event`), so a backend plugin sees `ClanMemberJoinedEvent`, `ClanDataChangedEvent` and the rest even when no member plays on this server.

### How the tag shows up

`ClanTags` registers a tab list contributor and a chat decorator, so the tag sits next to the name without touching the rank system:

- Chat: before the rank prefix or after the rank suffix (`clans.tag.position` in the proxy's `social.yml`), when `clans.tag.chat` is on. `%clan_tag%` is also available in `chat.line` of `messages_<language>.yml` for an own placement; set `clans.tag.chat: false` then.
- Tab list and name tag: as team prefix or suffix, when `clans.tag.tablist` is on. Minecraft uses one team for both, so they come together. The tab list updates right after a clan change, not only on the next refresh.
- Nicked players show no clan tag, the tag would give the real player away.
- Other tab plugins (TAB, ...) use the PlaceholderAPI placeholders below.

### PlaceholderAPI

| Placeholder | Value |
| --- | --- |
| `%prismarin_has_clan%` | `true` or `false` |
| `%prismarin_clan_tag%` | the formatted tag with `§` colours, empty without clan |
| `%prismarin_clan_tag_raw%`, `%prismarin_clan_name%`, `%prismarin_clan_color%` | tag, name and colour id (`gold`, `#ff8800`, empty) |
| `%prismarin_clan_role%`, `%prismarin_clan_role_id%` | role in the player's language (`Co-Leiter`) and as id (`co-leader`) |
| `%prismarin_clan_members%`, `%prismarin_clan_leader%` | member count, name of the leader |
| `%prismarin_clan_data_<key>%` | a value from the clan data, for example `%prismarin_clan_data_wars:points%` |

Classes: `BackendClanService`, `ClanMirror` (tested in `ClanMirrorTest`), `ClanTags`; `SocialView` carries `ClanView`, `ClanBadge` and the visible sub-commands for the lobby menus.

## Players: names, nicks, language, server and world

Only PRISMARIN knows whether a name is real or a nick, which language a player chose and where he is. `net.prismarin.api.player.PlayerService` hands all of it to other plugins, so a game plugin never rebuilds nick, vanish or locale logic. It is registered in the Bukkit `ServicesManager` (`Bukkit.getServicesManager().load(PlayerService.class)`), behind `PrismarinProvider.get().players()` and bound in the core injector, so Paper modules on a child injector can `@Inject PlayerService`. The proxy has its own instance with the same interface (see `core-velocity.md`).

### Read model

`NetworkPlayer` is an immutable snapshot:

| Field | Meaning |
| --- | --- |
| `uuid`, `realName` | the account |
| `nick`, `nicked()`, `shownName()` | the name everyone else sees right now. Only online players can be nicked |
| `locale` | the chosen language, same rule as `PlayerLocales.of` (menu choice first, then the client language) |
| `server`, `world`, `currentServer()`, `currentWorld()`, `onServer(name)` | where the player is, empty when offline or unknown |
| `online`, `vanished` | vanished is only true while online |
| `rank` | rank as everyone sees it (the disguise of a nicked player), `RankDisplay` with `label`, `prefix`, `suffix`, `nameColor`, `chatColor`, `weight` |
| `realRank` | the real rank, meant for the player himself and staff. Equal to `rank` unless nicked |
| `level`, `lastSeen` | from the profile |
| `source` | `LOCAL` (this server, exact), `PROXY` (live answer of the proxy) or `STORAGE` (profile and presence documents, may lag) |

Ranks are only known for players on this server. For players elsewhere `rank` is `RankDisplay.NONE` (`known()` is false). The real rank of a nicked player is read from his LuckPerms primary group, because the nick disguise overrides his own prefix; user specific prefixes are therefore not part of it.

Nicked players sort in the tab list by the weight of the fake rank: `fake-rank.weight` in `nick.yml`, otherwise the weight of `fake-rank.group`, or, when `fake-rank.prefix` is set, the weight of the LuckPerms group whose prefix shows the same text. They stand between the real members of the rank they show, so the position gives nothing away. TAB sorts by the real LuckPerms group; set its `sorting-types` to `PLACEHOLDER_HIGH_TO_LOW:%prismarin_rank_weight%` so nicked players sort by the fake rank there as well. PRISMARIN logs this hint on start when TAB is installed.

### Lookups

```java
PlayerService players = PrismarinProvider.get().players();

players.online(uuid).ifPresent(player -> scoreboard.line(player.shownName() + " in " + player.world()));
Locale language = players.locale(uuid);

players.find(friendUuid).thenAccept(found -> found.ifPresent(friend ->
        Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(
                players.displayName(viewer.getUniqueId(), friend) + " is on " + friend.server()))));

players.findAll(party.memberIds()).thenAccept(members -> { });
players.findByRealName("Leon").thenAccept(found -> { });
```

- `online`, `onlinePlayers`, `locale`, `displayName(viewer, uuid)`, `rank(viewer, uuid)`, `resolveOnline` and `suggestions` are synchronous and only know players on this server.
- `find`, `findAll`, `findByRealName` and `resolve` look network wide and return a `CompletableFuture` that completes off the main thread. Without Redis the server asks the proxy through any online player (message `player-query`, three seconds timeout, the proxy is skipped for a minute after a timeout). Without a player online or an answer it falls back to the document store: the `presence` collection (written by the proxy while friends, party or private messages are on, vanished players count as offline there), the nick owner and the profile. With `LOCAL` or `SQLITE` storage only this server's own documents exist.
- Never `join()` such a future on the main thread: the proxy answer arrives on the main thread, the lookup would wait for its timeout. Hop back with the scheduler instead.
- `findByRealName` ignores nicks on purpose and returns the full truth, use it for staff tools only.
- Social links without a second lookup: `players.party(uuid)`, `players.areFriends(first, second)`, `players.friendIds(uuid)`, `players.friends()` and `players.parties()` delegate to the friend and party API described above.

### Names the viewer may see

Nicks depend on who looks. The player himself, the console (`viewer` is `null`) and players with `prismarin.nick.realname` see the real name, everyone else the nick.

```java
String name = players.displayName(viewer.getUniqueId(), target.getUniqueId());
RankDisplay rank = players.rank(viewer.getUniqueId(), target.getUniqueId());
Component line = rank.prefix().append(Component.space()).append(rank.name(name));
boolean visible = players.canSee(viewer.getUniqueId(), snapshot);
```

`resolve(viewer, typed)` turns what a player typed into the right player without leaking anyone:

1. A visible online player whose shown name matches (nick or not nicked name).
2. For the player himself and staff also the real name of a nicked player.
3. Otherwise a matching real name returns the player as if he were offline (no nick, no server, no world). That is the same answer as for someone who really is offline, so typing the real name of a nicked or vanished player reveals nothing.
4. The nick of a vanished player the viewer cannot see finds nobody.

```java
players.resolve(sender.getUniqueId(), args[0]).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
    if (found.isEmpty() || !found.get().online()) {
        sender.sendMessage("Not online");
        return;
    }
    duel(sender, found.get().uuid());
}));
List<String> completions = players.suggestions(sender.getUniqueId(), args[0]);
```

`suggestions` lists nicks instead of real names and hides players the viewer cannot see. The rules live in `ViewerRules` (pure, tested in `ViewerRulesTest`) and can be used directly on any snapshot. Data keyed by UUID (stats, levels) can still connect a nick to the real player, show such data of a nicked player only to himself.

### Events

All on the `EventBus`, synchronous on the thread that saw the change:

| Event | When on Paper |
| --- | --- |
| `ServerChangedEvent(player, previousServer, currentServer)` | about 15 ticks after a player joined this server, once the proxy told where he came from. `joinedNetwork()` is true for a fresh login. Leaving is a normal `PlayerQuitEvent` |
| `LocaleChangedEvent(player, previous, current)` | the effective language changed (client setting or the lobby language menu) |
| `NickChangedEvent` (package `nick`) | nick applied or removed |

```java
prismarin.events().subscribe(LocaleChangedEvent.class, event -> boards.rebuild(event.player()));
prismarin.events().subscribe(ServerChangedEvent.class, event -> {
    if (event.joinedNetwork()) {
        welcome(event.player());
    }
});
```

### Extension point: player attributes

Plugins add their own per player data through `players.attributes()`, a `PlayerAttributes` registry. Every attribute becomes a PlaceholderAPI placeholder next to PRISMARIN's own and can add a line to the lobby profile head, without touching PRISMARIN.

```java
Subscription wins = players.attributes().register(PlayerAttribute.of("bedwars_wins",
        player -> String.valueOf(stats.wins(player.uuid()))));

Subscription rank = players.attributes().register(new PlayerAttribute() {
    @Override
    public String key() {
        return "bedwars_rank";
    }

    @Override
    public Optional<String> value(UUID viewer, NetworkPlayer target) {
        return stats.cachedRank(target.uuid());
    }

    @Override
    public Optional<Component> profileLine(UUID viewer, NetworkPlayer target, Locale locale) {
        return stats.cachedRank(target.uuid()).map(value -> Component.text("BedWars rank: " + value));
    }

    @Override
    public int order() {
        return 10;
    }
});

@Override
public void onDisable() {
    wins.unsubscribe();
    rank.unsubscribe();
}
```

- Keys are lower case `[a-z0-9_]`, at most 48 characters, and unique; `register` throws otherwise.
- `value` and `profileLine` may run on any thread and very often (scoreboards), so answer from memory and never do I/O there. `viewer` is the player looking, the target himself in his own profile, or `null` when no viewer is known (non relational placeholders). A provider that throws is skipped and logged once.
- Lines appear at the end of the lobby profile head. Operators can place them in `profile.head` of the lobby config with `attribute: <key>` (one) and `attributes` (all not placed yet).
- Read all of them with `attributes().values(viewer, target)` or one with `attributes().value(key, viewer, target)`.

### Extension point: rank sources

### How the rank appears

`rank-display` in `config.yml` decides how the rank is shown. Two placeholders exist: `%prefix%` is the prefix as LuckPerms (prefix, otherwise the group display name), a `RankSource` or the fake rank of a nick sets it; `%rank%` is the rank on its own, the LuckPerms meta named in `rank-display.luckperms-meta` (default `rank-label`) or the prefix with every character from `rank-display.trim` and all spaces cut off its end, colours and gradients kept.

| Key | Default | Used for |
|:---|:---|:---|
| `before-name` | `%prefix%` | tab list, name tag and chat (`%prefix%` in `chat.line`). Minecraft has one team prefix for tab list and name tag |
| `alone` | `%rank%` | sidebar, lobby profile, menus, `%prismarin_rank_label%` and `RankDisplay#label` |

With an existing setup like `&cAdmin &8┃` nothing has to change: the tab list keeps the separator, the sidebar shows `Admin`. For one style across all ranks, store plain ranks in LuckPerms (`&cAdmin`) and set `before-name: "%rank% <dark_gray>┃</dark_gray>"`. A nicked player's fake rank goes through the same lines, so it looks exactly like a real rank. `RankFormatProvider#prefix`, `#ownPrefix`, `#label` and `#ownLabel` return the results.

A rank plugin can supply the rank PRISMARIN shows in chat, tab list, name tag, sidebar and profile, instead of LuckPerms or the built in default rank. The source with the highest priority that returns a rank wins; a nicked player still shows the fake rank to others.

```java
Subscription ranks = PrismarinProvider.get().players().ranks().register(uuid -> myRanks.of(uuid)
        .map(rank -> new RankDisplay(rank.name(), rank.prefixComponent(), Component.empty(),
                rank.colour(), rank.chatColour(), rank.weight())), 100);

ranks.unsubscribe();
```

### PlaceholderAPI

New on top of the existing placeholders (`%prismarin_nick%`, `%prismarin_realname%`, `%prismarin_nicked%`, ...):

| Placeholder | Value |
| --- | --- |
| `%prismarin_locale%` | language tag of the chosen language, for example `de` |
| `%prismarin_language%` | name of that language in itself, for example `Deutsch` |
| `%prismarin_server_name%` | display name of this server from `servers.yml` |
| `%prismarin_world%` | current world |
| `%prismarin_vanished%` | `true` or `false` |
| `%prismarin_rank_label%`, `_prefix`, `_suffix`, `_color`, `_weight` | rank as everyone sees it, prefix and suffix with `§` codes, colour as a `§` code |
| `%prismarin_realrank_label%`, `_prefix`, `_suffix`, `_color`, `_weight` | the real rank, only show it to the player himself |
| `%prismarin_data_<key>%` | value of a registered attribute, empty when it has none, unknown keys stay as they are |
| `%rel_prismarin_rank_label%`, `_prefix`, `_suffix`, `_color`, `_weight` | real rank for the viewer himself and staff, otherwise the disguise |
| `%rel_prismarin_world%` | world of the target, empty when the viewer cannot see him |
| `%rel_prismarin_visible%` | whether the viewer can see the target (vanish) |
| `%rel_prismarin_data_<key>%` | attribute value for this viewer |

`%prismarin_nick%` and `%prismarin_realname%` now read the same snapshot, `%prismarin_locale%` follows the chosen language instead of the stored profile value.

### Channel `prismarin:moderation`

| Type | Direction | Content |
| --- | --- | --- |
| `player-state` | server to proxy, 20 ticks after join, on world change and on nick changes | `world`, `nick` (shown nick or empty) |
| `player-query` | server to proxy | `id`, `players[]` (UUIDs, at most 100), `names[]` (typed names, at most 10) |
| `player-answer` | proxy to server | `id`, `players[]` with `uuid`, `name`, `nick`, `server`, `world`, `locale`, `vanished` for every requested player that is online and every online player whose real name or nick matches a requested name |

Classes: `PaperPlayerService`, `PlayerTracker` (events, state reports), `ProxyPlayerQueries` (queries through a carrier), `RealRanks`, `LuckPermsGroupRank`, `RankDisplays`, `PaperPlayers` (registration); shared: `PlayerWire` (JSON, tested in `PlayerWireTest`), `PlayerSnapshots` (tested in `PlayerSnapshotsTest`). Placeholders are tested in `PlayerPlaceholdersTest` and `LegacyCodesTest`.

## Proxy services for backend plugins

`BackendApis.register` (after `BackendSocial`) makes the proxy's services usable on every backend and registers them behind `Prismarin` and in the Bukkit `ServicesManager`:

| Service | Backend implementation |
| --- | --- |
| `DataStorage` | `PluginDataStorage` on this server's document store |
| `AuditService` | `RemoteAudit`: writes directly with `MONGO` or `MYSQL`, otherwise sends the entry to the proxy and keeps it locally only without a route |
| `MaintenanceService` | `RemoteMaintenance`, every call goes to the proxy |
| `NotificationService` | `RemoteNotifications`, `Delivery.NO_ROUTE` without a route |
| `ReportService`, `PunishmentTemplates` | `RemoteReports`, `RemoteTemplates` with a `ModerationCatalog` for `reasons()`, `templates()` and `claimTimeout()`, refreshed at most once a minute and 40 ticks after a join |
| `NickService` | the `NickManager`, now also behind `Prismarin.nicks()` |
| `AltService` | `RemoteAlts`, every call goes to the proxy (`alts-find`) |
| `QueueService` | `QueueCalls`, every call goes to the proxy (`queue-*`), see the server queue below |

`PaperProxyLink` carries the calls: it picks an online player other than the one to avoid (a punished player could be kicked before the answer arrives), falls back to the Redis bus and only then to the avoided player, sends `network-request` with that player as carrier, and completes the call when `network-answer` arrives through the same player, after five seconds with a `TimeoutException`, or when the carrier quits. `network-event` messages from the proxy are published on the local `EventBus` (currently `ReportFiledEvent`). `ModerationChannel` only got the two message types and `handleRemote`.

## Server queue on backends

The queue itself runs on the proxy (see [`core-velocity.md`](core-velocity.md#server-queue)). Module `queue` in this server's `config.yml` (on by default) decides how the backend takes part:

- `/play <group>` and the group and server buttons of the lobby navigator go through `QueueingConnector.travel`, which calls `QueueService.join` on the proxy. `SENT` shows `play.connecting` or `lobby.navigator.connecting`, a queued player hears from the proxy (`queue.joined` and the action bar), `NO_SERVER` shows `play.no-server`. When the proxy has no queue (`DISABLED`) or cannot be reached, the old direct connect runs instead. With the module off here, `/play` and the navigator connect directly as before, and the proxy still queues connections to full servers.
- `prismarin.connector()` is the same `QueueingConnector`: `connect(player, server)` sends `Connect` as before (the proxy queues it when the server is full), `connectToGroup(player, group)` joins the group queue and returns the server only when the player was sent right away, an empty `Optional` while he waits. Use `prismarin.queue().join(...)` for the details.
- `QueueBypassListener` lets players with `prismarin.queue.bypass`, `prismarin.queue.bypass.<server-id>` or `prismarin.queue.bypass.<group>` join although the server is full (`PlayerLoginEvent` with `KICK_FULL`), so staff the proxy lets through are not turned away here.
- Game plugins hold the queue with `prismarin.gameState().state(GameState.RUNNING)` (the proxy reads the state from the ping) or with `prismarin.queue().orElseThrow().joinable(prismarin.serverId(), false)` and open it again with `true`.

`QueueService` is always registered on Paper, in `Prismarin.queue()`, `QueueApi` and the `ServicesManager`; while the proxy has no queue, `join` answers `DISABLED` and the other calls fail with the proxy's reason. The queue events arrive from the proxy as `network-event` and fire on the local `EventBus`.

## Reload, update check and metrics

`/prismarin reload` (permission `prismarin.admin.reload`) reloads the language files and the rank display plus every part another plugin registered through `prismarin.reloads()`, and lists what failed. Storage, Redis and module switches need a restart.

`update-check` in `config.yml` asks `api.github.com/repos/LeonManuel0705/prismarin/releases/latest` on start and every `interval-hours` (default 6). A newer release is logged once, fires `UpdateAvailableEvent` and, with `notify-staff`, shows staff with `prismarin.admin.update` a clickable note on join.

`metrics.enabled` sends anonymous bStats data (bstats.org): the standard charts plus `storage_mode`, `redis`, `default_language` and `lobby`. bStats is shaded and relocated to `net.prismarin.libs.bstats`, `plugins/bStats/config.yml` switches it off for every plugin.

## Chat filter

`PaperChatFilter` (module `chat-filter`) checks every chat message before it is sent. It listens to `AsyncChatEvent` at priority `LOW` with `ignoreCancelled`: muted players are already stopped by `MuteListener` (`LOWEST`), and the filter runs before PRISMARIN's chat format (`NORMAL`) and before most chat plugins. It works the same when `modules.chat` is off and another plugin formats the chat. A blocked message is cancelled, a changed one continues with the new text. With `compatibility.auto-detect` the module steps aside when ChatSentry, ChatControl or ChatControlRed is installed.

The rules come from the shared engine (`net.prismarin.shared.chatfilter`, see [`shared.md`](shared.md#chat-filter-engine)) in this order: `flood`, `repeat`, `words`, `links`, `caps`, then plugin rules. A `flood` block stops the check, every other rule is checked so staff see all hits. Flood and repeat are counted per player and channel and forgotten on quit.

### `chat-filter.yml`

Written with comments on the first start, missing keys are added on every start. Every rule has `enabled`, `action`, `notify-staff`, `audit`, `skip-channels` and an `escalation` block.

| Key | Default | Meaning |
| --- | --- | --- |
| `replacement` | `***` | replaces a word or a link when the action is `replace` |
| `<rule>.action` | `words` block, `caps` replace, `links` block, `repeat` block, `flood` block | `block` (not sent, the writer is told why), `replace` (only `words`, `links` and `caps`; caps are written small), `warn` (sent, the writer gets a warning), `log` (sent unchanged, only staff and the audit log see it) |
| `<rule>.notify-staff` | `true` for `words` and `links` | alert on the proxy for `prismarin.staff.chatfilter` |
| `<rule>.audit` | `true` for `words` and `links` | audit entry `chat-filter.<action>` on the proxy |
| `<rule>.skip-channels` | `repeat: [private]`, otherwise `[]` | channels the rule ignores (`public`, `private`, `party` or a plugin's channel) |
| `<rule>.escalation.enabled` | `true` except for `caps` | count hits on the proxy |
| `<rule>.escalation.violations`, `.within` | `words` 3 in `10m`, `links` 3 in `30m`, `repeat` 5 in `5m`, `flood` 6 in `2m`, `caps` 10 in `10m` | threshold and window |
| `<rule>.escalation.template` | `chat` for `words`, `spam` otherwise | template from `punishments.yml`, empty for none |
| `<rule>.escalation.report`, `.report-reason` | `true` for `words` (`chat`) and `links` (`spam`) | automatic report with the counted messages as chat evidence |
| `words.list` | 29 common German and English insults and slurs | a plain entry matches whole words only, `word*` words that start with it, `*word` words that end with it, `*word*` any word containing it; entries with spaces match words in a row |
| `words.patterns` | `[]` | regular expressions on the original text, without case |
| `words.allowed` | `[]` | words that never count |
| `caps.percent`, `caps.min-letters` | `70`, `8` | share of capitals; shorter messages such as OK, GG or LOL are never checked |
| `links.block-ips` | `true` | IPv4 addresses with or without port |
| `links.allowed-domains` | `[]` | allowed domains including their subdomains, for example your own |
| `links.top-level-domains` | 67 common endings | endings that count without `http://`; addresses with `http://` or `https://` always count |
| `repeat.similarity`, `.within`, `.history`, `.min-length` | `85`, `30s`, `3`, `4` | similarity in percent (edit distance on the simplified text) to one of the last messages |
| `flood.messages`, `flood.within` | `4`, `5s` | messages allowed in the window; blocked attempts count too |
| `custom.<id>` | `{}` | settings for plugin rules by id; an empty `action` keeps the plugin's choice |

How lenient the word match is: case, accents and full width letters are folded (NFKD, `ß` as `ss`), look-alike Cyrillic and Greek letters count as Latin ones, leetspeak is read (`0 o`, `1 i`, `3 e`, `4 a`, `5 s`, `7 t`, `8 b`, `9 g`, `@ a`, `$ s`, `! i`, `| i`, `€ e`, `£ l`), every letter may repeat (`fuuuck`) while doubled letters of an entry stay required (`niger` never matches `nigger`), dots, dashes and zero width spaces inside a word are ignored (`f.u.c.k`), single letters with spaces between them are joined (`f u c k`) and punctuation splits words (`you,idiot`). Replacements keep the punctuation around a word (`idiot!` becomes `***!`). Links are also found as `example dot com`, `example (.) com`, `example [dot] net`, `example . com` and with the ideographic full stop; e-mail addresses do not count.

### Permissions and texts

| Permission | Default | Effect |
| --- | --- | --- |
| `prismarin.chatfilter.bypass` | `op` | skips the whole filter |
| `prismarin.chatfilter.bypass.<rule>` | not declared, so `op` | skips one rule, for example `prismarin.chatfilter.bypass.caps` |

Texts under `chat-filter` in `messages_de.yml` and `messages_en.yml`: `blocked`, `changed`, `warned` with `%reason%` from `reason.<rule>`; plugin rules use `reason.custom` unless the operator adds `reason.<id>`. Only the strongest action is told: block, then warn, then replace.

### What goes to the proxy

When a hit needs the proxy (alert, audit or escalation), the filter sends `chat-violation` on `prismarin:moderation` through the writing player, on the main thread:

| Field | Content |
| --- | --- |
| `uuid`, `name`, `channel` | the writer and the channel |
| `message`, `shown`, `blocked` | the original text (at most 512 characters), the shown text (empty when blocked) |
| `entries[]` | per rule `rule`, `action`, `detail` (word, domain, percentage), `alert`, `audit`, `escalation` (`enabled`, `violations`, `window` in ms, `template`, `report`, `report-reason`) |

The proxy takes it only from a `ServerConnection` whose player is `uuid`, checks every field again (rule ids, actions, lengths, ids of templates and reasons, at most 16 entries, windows between one second and seven days) and does the rest, see [`core-velocity.md`](core-velocity.md#chat-filter-on-the-proxy). Without a PRISMARIN proxy, or with its module off, the backend still blocks, changes and warns.

### API

`PaperChatFilter` is the `ChatFilterService` behind `Prismarin.chatFilter()` and in the `ServicesManager` while the module is on (see [`api.md`](../api.md#chat-filter)). `filter` uses the online player for bypass permissions and the text to the writer, `check` never touches player state. `ChatFilteredEvent` is fired on the local `EventBus`, on the chat thread.

Classes: `chatfilter/PaperChatFilter`; `ModerationChannel.sendChatViolation`; `CoreModule` provides `ChatFilterSettings`. Tests: `ChatFilterMessagesTest` (both languages, placeholders, a reason per rule) and the engine tests in `:shared`.

## Stats and leaderboards

Module `stats` in `config.yml`. `PaperStats` builds the shared `DocumentStatsService` (see [`shared.md`](shared.md#stats-storage)) with the settings of `stats.yml`, tracks every player on this server (loaded on join together with the pull sources, forgotten on quit), rebuilds the top lists that are in use every `leaderboards.refresh-seconds` on an async task, reads the shared `stat-catalog` (names, formats and games other servers registered through the API) on start and every five minutes, and runs the placeholder sync below. Stats of other plugins come in three ways: pushed through the API, pulled from a registered `StatSource`, or read through PlaceholderAPI (see [`api.md`](../api.md#stats-of-other-plugins)). It is the `StatsService` behind `Prismarin.stats()` and in the `ServicesManager`. With the module off there is no `/stats`, no placeholder, no profile entry and no leaderboard in the lobby.

### `stats.yml`

Written with comments on the first start, missing keys are added on every start, mistakes are logged and replaced by the default.

| Key | Default | Meaning |
| --- | --- | --- |
| `periods.time-zone` | `server` | zone of the weekly and monthly reset, for example `Europe/Berlin` |
| `periods.reset-time` | `00:00` | wall clock time the new week and month begin |
| `periods.week-starts` | `monday` | first day of the week (English or German names, `mon`, `sun`, ...) |
| `periods.weekly`, `periods.monthly` | `true` | `false` turns the totals and their top lists off |
| `leaderboards.refresh-seconds` | `60` | 5 to 3600, how often a top list in use is rebuilt |
| `leaderboards.cache-size` | `100` | entries kept per top list, ranks below are counted from the values |
| `leaderboards.shared-ranks` | `false` | `true`: equal values share the rank (1, 1, 3); `false`: who reached the value first ranks higher |
| `show-unlisted` | `true` | stats that are not listed under `stats` still show in `/stats` with a name made from the key (`bedwars.final-kills` as "Final kills") and a format guessed from the unit (`-millis`, `-ms`, `-seconds`) |
| `sources.timeout-millis` | `3000` | 250 to 30000, longest wait for a pull source; a slower or failing source is skipped and the stored values show (a warning at most every five minutes per source) |
| `placeholder-sync-minutes` | `5` | 0 to 1440, how often the placeholder stats of the players on this server are read and written; the same run reads their pull sources again for the cached placeholders. `0` reads only on join and on leave |
| `placeholder-join-delay-seconds` | `10` | 0 to 300, wait after a join before the placeholders are read, so the other plugin can load its data first |
| `placeholder-decimal-separator` | `auto` | `auto`, `.` or `,`: how numbers from placeholders are read, see below |
| `command.page-size` | `10` | 3 to 30 stat lines (or games) per page of `/stats` |
| `command.game-list-above` | `3` | with more games than this, `/stats` first shows the clickable list of games |
| `profile.enabled`, `profile.limit` | `true`, `6` | stats entry in the lobby profile, at most `limit` stats with `in-profile: true` |
| `built-in.votes`, `built-in.daily-claims`, `built-in.daily-streak` | `votes`, `daily.claims`, `daily.streak` | stats PRISMARIN counts itself: one per vote reward, one per daily claim, the best login streak (`record`); empty turns one off |
| `games.<game>.name` | `general`, `daily`, `example` | game names in `/stats`: a text, a message key or one text per language |
| `games.<game>.icon`, `.color`, `.order`, `.featured` | `NETHER_STAR`/`#22D3EE`/`0`/`[votes]`, `CHEST`/`#FBBF24`/`10`/`[streak]`, `DIAMOND_SWORD`/`#F472B6`/`20`/`[wins]` | material for menus, colour (`#RRGGBB` or a name such as `gold`), order (lower first, default `100`), stats shown first; every value set here wins over `defineGame`, missing ones come from the plugin |
| `stats.<key>.name` | seeded for `votes`, `daily.streak`, `daily.claims`, `example.wins`, `example.games` | a text, a message key or `en`/`de`/... |
| `stats.<key>.format` | `number` | `number` (1,234), `plain`, `duration` (seconds as 1:02:03), `millis` (1:02.345), `decimal` (value / 100) |
| `stats.<key>.order` | `highest` | `highest` or `lowest` (fastest time first) |
| `stats.<key>.in-stats`, `.in-profile` | `true`, `false` | shown in `/stats`, shown in the lobby profile |
| `stats.<key>.placeholder` | none | a PlaceholderAPI placeholder such as `"%bedwars_stats_wins%"` (or a text with one, `"Level %bedwars_level%"`) that holds the value |

### PlaceholderAPI stats

For plugins without an API: `stats.bedwars.wins.placeholder: "%bedwars_stats_wins%"`. On every server with PlaceholderAPI, `PaperStats` reads these placeholders on the main thread for the players there: `placeholder-join-delay-seconds` after a join, every `placeholder-sync-minutes` after that (spread by join time, `StatSyncSchedule`), when the player leaves, when `/stats` is opened for a player on this server and once more on shutdown. The values go into the shared stats store (one update per player, only when something changed), so `/stats`, `%prismarin_stat_...%`, ranks and the lobby leaderboards work on every server, with `MONGO` or `MYSQL` network wide.

A value is only written by a server where it resolves: the expansion (the part between `%` and the first `_`) must be registered in PlaceholderAPI, the result must not contain the placeholder any more and must hold a number. A lobby without the BedWars plugin, a server without PlaceholderAPI, an empty answer or a text such as `N/A` or `Loading...` write nothing, so they never overwrite the value with 0. The first value of a key does not count into the running week and month, later changes count by their difference.

Numbers are read tolerantly (`StatNumbers`): colour codes, MiniMessage tags and text around the number are dropped (`Wins: 1,234` is 1234), spaces, non breaking spaces and apostrophes between digits are thousands separators, `k`, `M` and `B` multiply (`1.5k` is 1500). With `placeholder-decimal-separator: auto` two separators mean the last one is the decimal point (`1.234,5`, `1,234.5`), one separator with exactly three digits after it is a thousands separator (`1.234`), otherwise a decimal point (`1.5`, rounded for whole number stats); `.` or `,` fix the decimal separator. The format of the stat changes how a value is stored: `decimal` keeps two decimals (`1.25` is 125), `duration` reads `1:02:03` or `1h 2m 3s` as seconds, `millis` reads `1:02.345` as milliseconds.
### `/stats`

| Command | Effect | Permission |
| --- | --- | --- |
| `/stats [game] [page]` | your own stats, grouped by game in the game's name and colour, with the all time value and the rank where a top list exists; hover shows this week and this month | `prismarin.command.stats` (`true`) |
| `/stats <player> [game] [page]` | stats of another player, only one game with the second argument | `prismarin.command.stats.others` (`true`) |

`StatsArguments` reads the arguments: a first argument that is a known game (and no player on this server with that name) means the own stats of that game; a number in place of the game is a page. Games come from `stats.yml`, `defineGame`, the shared catalog, definitions and sources, sorted by `order`; inside a game the `featured` stats come first, then the order of `stats.yml`, then the rest by key. With more games than `command.game-list-above`, `/stats` without a game shows the list of games instead (`stats.games-header`, one `stats.game-entry` per game with the number of stats and the first featured value, a hover with up to six values, a click opens `/stats <player> <game>`). Long lists are paged by `command.page-size` with clickable `« Back` and `Next »` links (`stats.page`); a single game shows `« All games` back to the list. Ranks are only looked up for the lines on the page.

The typed name resolves like every PRISMARIN command (`PlayerService.resolve`). A nicked player that the viewer may not unmask shows as "has no stats yet" under the nick, so `/stats` never links a nick to the real numbers. Texts under `stats.*` (`header`, `header-game`, `game`, `line`, `rank`, `hover`, `games-header`, `game-entry`, `game-first`, `game-hover-line`, `game-hover-more`, `game-hover-click`, `page`, `page-previous`, `page-next`, `page-previous-none`, `page-next-none`, `page-hover`, `back`, `back-hover`, `none`, `none-game`, `failed`, `usage`, `usage-self`, `period.alltime|weekly|monthly`, `menu.*` for the lobby profile).

### PlaceholderAPI

| Placeholder | Value |
| --- | --- |
| `%prismarin_stat_<key>%` | formatted all time value of the player (players on this server, `0` otherwise) |
| `%prismarin_stat_<key>_<period>%` | `alltime`, `weekly` or `monthly` |
| `%prismarin_stat_<key>[_<period>]_raw%` | the plain number |
| `%prismarin_stat_<key>[_<period>]_rank%` | rank of the player, empty until the list is built |
| `%prismarin_top_<key>[_<period>]_<n>_name%` | name on place `n` (1 to 100), with the nick of a nicked player who is online and visible |
| `%prismarin_top_<key>[_<period>]_<n>_value%`, `_raw`, `_uuid` | formatted value, plain value, UUID |
| `%prismarin_daily_streak%`, `_best`, `_claims`, `_next`, `_available` | daily reward state of the player |

Values of pull sources and placeholder stats show up here too: tracked players are loaded with their sources on join and refreshed by the sync, top lists come from the source or from the mirrored values. Top placeholders read the cache; the first request for a list starts its build, so it shows up a moment later. A switched off period answers with an empty text. Parsing is in `StatPlaceholder` (tested in `StatPlaceholderTest`), resolving in `StatPlaceholders` (`StatPlaceholdersTest`).

Classes: `stats/PaperStats`, `StatsSettings`, `StatsCommand`, `StatsArguments`, `StatsView` (grouping, featured stats, game list and pages), `StatTexts` (game name and colour, a fixed palette for games without one), `StatPlaceholder`, `StatPlaceholders`, `PlaceholderStats` (reading placeholder stats), `StatSyncSchedule`; `bridge/PlaceholderApiStatResolver` (only loaded with PlaceholderAPI); `locale/ConfiguredText` renders a text that may be a message key or MiniMessage. Tests: `StatsSettingsTest`, `StatsViewTest` (grouping, order, featured, paging, game list), `StatsArgumentsTest`, `StatPlaceholderTest`, `StatPlaceholdersTest` (also pull sources), `PlaceholderStatsTest` (parsing, missing plugins, no overwrite from servers without the plugin), `StatSyncScheduleTest`, `StatsMessagesTest`.

## Daily rewards

Module `daily-rewards` in `config.yml`; it steps aside when DailyRewards or DailyRewardsPlus is installed (`compatibility.auto-detect`). `PaperDailyRewards` is the `DailyRewardService` behind `Prismarin.dailyRewards()`.

| Command | Effect | Permission |
| --- | --- | --- |
| `/daily` (`/dailyreward`) | claims today's reward or says when the next one is ready | `prismarin.command.daily` (`true`) |

On join the status is loaded; with `reminder.on-join` a player with an open reward gets `daily.reminder` after `reminder.delay-seconds`, click runs `/daily`. The reminder only comes when the player joins the network (`ArrivalTracker` knows no previous server), not after every server switch. The lobby profile shows the reward when `profile-entry` is on (see [`lobby.md`](lobby.md#profile-entries-for-stats-and-daily-rewards)).

### `daily-rewards.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `time-zone`, `reset-time` | `server`, `00:00` | when a new day begins |
| `grace-days` | `0` | days a player may miss without losing the streak; `0` means a missed day starts again at day 1 |
| `reminder.on-join`, `reminder.delay-seconds` | `true`, `5` | reminder on join |
| `profile-entry` | `true` | entry in the lobby profile menu |
| `cycle` | `7` | length of the cycle; day 8 gives the reward of day 1 again |
| `days.<n>` | days 1 to 7: 50, 75, 100, 125, 150, 200, 300 coins and 25 XP per day, day 7 also 5 gems and a message | reward per day of the cycle |
| `default` | 50 coins | reward for days without an entry |
| `milestones.<n>` | 30: 25 gems and a message, 100: 100 gems | extra reward when the streak reaches exactly `n`, on top of the day |

A reward is `currencies` (any id of `economy.currencies`, unknown ones are logged), `xp`, `levels`, `commands` (run by the console on the main thread, `%player%`, `%uuid%`, `%streak%`, `%day%`, `%source%` replaced, a leading `/` is dropped), `message` or `messages` (a text, a message key or one text per language, the same placeholders) and `custom` (ids registered through `RewardApi.handlers()`).

### Claims, streaks and double claims

The state of a player is one document `daily-rewards/<uuid>` (`streak`, `best`, `claims`, `lastDay` as epoch day in the configured zone, `lastAt`). `claim` runs a single `DocumentStore.update`: it reads the document, computes the next streak with `Streaks.next` and writes only when today was not claimed yet. JSON files lock in the process, SQLite runs `BEGIN IMMEDIATE`, MySQL and MariaDB lock the row with `SELECT ... FOR UPDATE` and retry a first insert that collides, MongoDB replaces only when the body is still the one it read. Two servers that claim at the same moment therefore see `CLAIMED` once and `ALREADY_CLAIMED` once, and only the winner pays. A streak continues when the gap is at most `1 + grace-days` days, otherwise it starts at 1 and `DailyStreakBrokenEvent(player, lostStreak, missedDays)` fires. After paying, `DailyRewardClaimedEvent(player, claim)` fires. With the stats module on, `built-in.daily-claims` counts up and `built-in.daily-streak` records the best streak.

Texts under `daily.*` and `reward.*` (`reward.currency`, `reward.xp`, `reward.level(s)`, `reward.extra`, `reward.line` build the reward lines). Classes: `reward/PaperDailyRewards`, `DailyRewardSettings`, `DailyCommand`, `RewardGranter` (pays any `Reward`), `RewardSummary`, `RemainingTime`; shared `DailyLedger`, `DailySchedule`, `Streaks`, `DayClock`. Tests: `DailyRewardSettingsTest`, `RemainingTimeTest`, `RewardMessagesTest` and the shared ledger tests.

## Vote rewards

Module `vote-rewards` in `config.yml`; it steps aside when VotingPlugin or SuperbVote is installed. Votes are counted and queued on the proxy (see [`core-velocity.md`](core-velocity.md#votes)); this module hands them out:

1. 3 seconds after a join, and 1 second after a relayed `VoteReceivedEvent` for a player on this server, `VoteRewards` asks the proxy with `vote-claim` (`player`, `name`, `limit` at most 10 per call, repeated until the queue is empty).
2. The proxy deletes every vote it returns, so a vote reaches exactly one server even when two ask at once.
3. Each vote's reward is paid through `RewardGranter` (`%service%` is the vote site), `VoteRewardGivenEvent(player, vote)` fires, `built-in.votes` counts up and the player reads `vote.rewarded` or `vote.rewarded-many`.

`Prismarin.votes()` on a backend forwards every call to the proxy (`RemoteVotes`) and is registered whether this module is on or not.

## JoinMe

Module `joinme` in `config.yml` (on by default). The proxy keeps the JoinMes, the cooldowns and the audience (see [`core-velocity.md`](core-velocity.md#joinme)); this module owns the command, the cost and what happens when a player arrives. `PaperJoinMe` is the `JoinMeService` behind `Prismarin.joinMe()` and in the `ServicesManager`, and it registers `prismarin.skinFaces()`. Keep the module on on every server whose players are in the audience: the click runs `/joinme join <id>` there.

| Command | Effect | Permission |
| --- | --- | --- |
| `/joinme` | starts a JoinMe for your game; without `prismarin.joinme` it lists the open ones | `prismarin.joinme` (`op`) |
| `/joinme preview` | sends the JoinMe message only to you, exactly as `/joinme` would (handler, placeholders, face rows, your skin face, a nick's skin with a nick), on every server including lobbies and blocked groups, without cooldown or cost; nothing is created, the click does nothing | `prismarin.joinme` |
| `/joinme list` | open JoinMes you may see (audience filter of friends, party, clan), each line clickable | `prismarin.command.joinme` (`true`) |
| `/joinme cancel` | ends your own JoinMe | `prismarin.joinme` |
| `/joinme cancel <id\|player>` | ends any JoinMe, by id, real name or nick | `prismarin.joinme.cancel` (`op`) |
| `/joinme help` | the parts you may use | `prismarin.command.joinme` |
| `/joinme join <id>` | the click of the message; never in help, tab completion or menus | `prismarin.command.joinme` |

The cooldown, cost, lifetime and join tiers (`prismarin.joinme.cooldown.<tier>`, `.cost.<tier>`, `.lifetime.<tier>`, `.joins.<tier>`) are checked by the proxy. `JoinMeSubCommands` decides what is visible (tested in `JoinMeSubCommandsTest`): a switched off or not permitted part disappears from help and tab completion, typing it shows the help.

### Creating a JoinMe

On the main thread `PaperJoinMe` refuses vanished players, builds the `JoinMeSpot` (creator, `server-id`, its group, world, the game state of this server), picks a handler (see below), refuses when its `allows(spot)` is false, and collects the placeholders `players`, `max_players`, `world_players`, the handler's `placeholders(spot)`, the request's own values, the handler's `lines(spot)` and the value of the `textures` property of the creator's profile (with a nick exactly the skin the others see). Then `joinme-check` goes to the proxy, `JoinMeCreateEvent` fires here (cancellable, change placeholders, face rows, lifetime, max joins, audience), the cost is withdrawn through `Prismarin.economy()` (an unknown currency logs a warning and stays free), and `joinme-create` follows. When the proxy refuses at this point the money is paid back. Results come back as `JoinMeResult` and the command shows `JoinMeTexts.created(status)`.

### Arriving players

The proxy sends `joinme-arrival` when the player is on this server. After `arrival.delay-ticks` (so join plugins finish first):

1. The handler is chosen: the one that took the JoinMe (`JoinMe.handler()`) if it is still registered, otherwise the best one for the creator's current spot. Its `arrive(arrival)` decides: `HANDLED` (the plugin placed the player), `FALLBACK` (go on with the fallback) or `REFUSED`. An exception counts as `FALLBACK` and is logged.
2. Without handler or on `FALLBACK`, with `fallback.same-world` the player is put into the creator's current world: next to the creator with `fallback.position: creator` when the creator is in that world, else to the world spawn (nothing happens when he already is in that world). Without it he stays where the server put him.
3. On `REFUSED` the player reads `joinme.arrival-refused` and with `arrival.send-back-when-refused` goes back to the server he came from.
4. `joinme.arrived` (or `joinme.arrived-party` for party members who followed) tells the player whom he joined, then `JoinMeArrivedEvent(player, joinMe, placement, handler, party)` fires with `HANDLER`, `NEAR_CREATOR`, `WORLD_SPAWN`, `SERVER` or `REFUSED`.

The decision is `ArrivalPlan` in `:shared` (tested in `JoinMeFallbackTest`); `JoinMeRoute` decides on the proxy whether a click connects, only arrives (same server, other world) or is refused (`ALREADY_THERE`, `OTHER_SERVER`).

### Preview

`/joinme preview` (and `JoinMeService.preview`) runs the same steps as a creation on the main thread, with three differences: vanish and the handler's `allows(spot)` do not refuse, `JoinMePreviewEvent` fires instead of `JoinMeCreateEvent` (placeholders and face rows can be changed the same way), and the draft goes to the proxy as `joinme-preview`, carried by the creator. The proxy renders it with its `joinme.yml` (face, rows, texts, title and sound) and sends it only to the creator, see [`core-velocity.md`](core-velocity.md#preview). The game server then shows `joinme.preview-sent`; problems show `joinme.disabled`, `joinme.unavailable` or `joinme.failed` (`JoinMeTexts.previewed`). No JoinMe is stored, no cooldown starts, nothing is charged.

### Handlers

```java
prismarin.joinMe().ifPresent(joinMe -> joinMe.registerHandler(JoinMeScope.worlds("arena-1", "arena-2"), handler));
```

Scopes: `JoinMeScope.server()` (everything on this server, the default of `registerHandler(handler)`), `groups(...)`, `worlds(...)` and `when(spot -> ...)` for anything else, such as "the creator is in one of my arenas". The best handler is the one with the highest `priority()`, then the most specific scope (`when` 3, `worlds` 2, `groups` 1, `server` 0), then the earliest registration (`JoinMeHandlerRegistry`, tested in `JoinMeHandlerRegistryTest`). A failing scope is skipped. Ids match `[a-z0-9][a-z0-9_.:-]{0,47}`.

### `joinme.yml` on the game server

| Key | Default | Meaning |
| --- | --- | --- |
| `fallback.same-world` | `true` | put players into the creator's world when no handler places them |
| `fallback.position` | `spawn` | `spawn` (world spawn) or `creator` (next to the creator) |
| `arrival.delay-ticks` | `10` | wait after the join, 0 to 200 |
| `arrival.send-back-when-refused` | `true` | back to the previous server when a handler refuses |
| `arrival.message` | `true` | `joinme.arrived` |
| `sub-commands.list`, `sub-commands.cancel`, `sub-commands.preview` | `true` | `false` hides that part everywhere (help, tab completion); `preview` also needs `prismarin.joinme` to show |
| `lobby.navigator-entry` | `true` | the JoinMe entry in the lobby navigator |
| `face.cache-size`, `face.disk-cache`, `face.download-timeout-millis` | `512`, `true`, `3000` | the `prismarin.skinFaces()` cache of this server |

The world fallback is per server, so a multi world server can use `creator` while others use `spawn`. Without a handler, `fallback.same-world: false` here and `join.same-server: false` on the proxy, `/joinme` answers `joinme.nothing-to-join`. `/prismarin reload` reloads this file (the part `joinme`), `/prismarinproxy reload` the proxy's `joinme.yml`; the face cache sizes need a restart.

### Texts

Under `joinme.*` in the PrismarinCore message files: `created`, `cooldown` (`%time%`), `disabled`, `group-blocked`, `game-state`, `vanished`, `refused`, `nothing-to-join`, `not-enough-money`, `cancelled`, `unavailable`, `failed`, `joining`, `join-not-found`, `join-expired`, `join-used-up`, `join-own`, `join-already-there`, `join-other-server`, `join-server-offline`, `join-game-state`, `join-cancelled`, `arrived`, `arrived-party`, `arrival-refused`, `list-header`, `list-entry`, `list-hover`, `list-empty`, `cancel-usage`, `cancel-own`, `cancel-none`, `cancel-done`, `cancel-unknown`, `preview-sent`, `help-*` (also `help-preview`), `state.<state>` and `menu.*` for the lobby. The placeholders are the ones of the broadcast (see `core-velocity.md`). Classes: `joinme/PaperJoinMe`, `JoinMeCommand`, `JoinMeSubCommands`, `JoinMeTexts`, `JoinMeView`, `BackendJoinMeSettings`. Tests: `JoinMeSubCommandsTest`, `BackendJoinMeSettingsTest`, `JoinMeMessagesTest`.

## Cosmetics

Module `cosmetics` in `config.yml` (on by default); it steps aside when UltraCosmetics, ProCosmetics, GadgetsMenu or HMCCosmetics is installed (`compatibility.auto-detect`). `PaperCosmetics` is the `CosmeticService` behind `Prismarin.cosmetics()` and in the `ServicesManager` (API in [`docs/api.md`](../api.md#cosmetics)). With the module off there is no `/cosmetics`, no placeholder, no lobby item, no `cosmetic` reward type, no listener and no task.

Categories are slots, a player wears one cosmetic per slot: `trail` (particles that follow while walking), `aura` (particles around a standing player, also while walking when no trail is worn), `hat` (any item or block or a player head by texture on the head slot), `projectile-trail` (follows the arrows, tridents, snowballs and other projectiles the player shoots), `kill-effect` (plays where the victim fell) and `win-effect` (plays through `/cosmetics play` or the API). Projectile trails and kill effects play by themselves, no game plugin needed (see [Automatic triggers](#automatic-triggers)). Plugins can add slots of their own.

### Where they show

A category is active on a server when it is `enabled` and the server's group in `servers.yml` (or its `server-id`) is in the category's `groups`, or in `active-groups` when the category lists none. `"*"` means every server. The defaults: `active-groups: [lobby]`, the three effect categories `["*"]`, because they only play in the moments that call for them. A server that is missing from `servers.yml` but runs PrismarinLobby counts as `lobby`. Game plugins switch a category for this server with `enable(slot)`, `disable(slot)` and `reset(slot)` (`ActiveSlots`, tested in `ActiveSlotsTest`); a category that is off in `cosmetics.yml` stays off. Ownership is network wide, so a hat bought in the lobby is also worn on a game server as soon as the game switches hats on, and a kill effect bought in the lobby plays on every game server.

### Automatic triggers

PrismarinCore runs on every Paper server, so most game plugins never have to call the API:

- **Projectile trails.** When a player shoots or throws a projectile (`ProjectileLaunchEvent`, bows, crossbows and throwing alike) whose type is in `triggers.projectile-trail.projectiles` (default `arrow`, `spectral_arrow`, `trident`, `snowball`, `egg`, `ender_pearl`; `fishing_bobber`, `wind_charge`, `splash_potion`, `lingering_potion` and `experience_bottle` can be added), his worn projectile trail follows it every `interval-ticks` until it hits a block, sticks in the ground, is removed, leaves through an unloaded chunk or flew `max-ticks`. At most `max-tracked` projectiles have a trail at once. One task draws all of them and only exists while at least one projectile is tracked (`ProjectileTrails`, tested in `ProjectileTrailsTest`). Trails leave when the shooter quits.
- **Kill effects.** When a player kills another player (`PlayerDeathEvent` with a killer), the killer's kill effect plays where the victim fell. `triggers.kill-effect.mob-kills: true` also plays it for mobs he kills. Self kills, armor stands and effect entities never count. `KillTrigger` decides (tested in `KillTriggerTest`): automatic trigger on, a killer, not himself, not an effect entity, a player victim or mob kills on, the category active here, the killer not vanished and not spectating.
- **Win effects.** There is no common win event in Bukkit, so they play through `/cosmetics play` (below) or `playEffect`.

A game plugin that wants to decide itself, for example only final kills, sets `triggers.kill-effect.auto: false` (or `triggers.projectile-trail.auto: false`) in `cosmetics.yml`, or calls `setAutomatic(CosmeticSlot.KILL_EFFECT, false)` for this server at runtime (`resetAutomatic` goes back to the file) and then plays effects through `playEffect` or `/cosmetics play`. Every trigger also fires the cancellable `CosmeticEffectEvent` with its `Trigger` (`KILL`, `PROJECTILE`, `COMMAND`, `API`), so a plugin can also veto single cases.

### `/cosmetics`

| Command | Effect | Permission |
| --- | --- | --- |
| `/cosmetics` (`/cosmetic`) | opens the menu | `prismarin.command.cosmetics` (`true`) |
| `/cosmetics <category>` | opens one category, for example `/cosmetics hat` | same |
| `/cosmetics hide` | switches the particles of other players off and on (`sub-commands.hide`) | same |
| `/cosmetics give <player> <cosmetic>` | gives a cosmetic (source `STAFF`), also to offline players | `prismarin.command.cosmetics.give` (`op`) |
| `/cosmetics take <player> <cosmetic>` | takes one away and off | `prismarin.command.cosmetics.take` (`op`) |
| `/cosmetics play <player> <category> [<x> <y> <z>\|here]` | plays the effect the player wears in that category, hidden | `prismarin.command.cosmetics.play` (`op`, the console may) |
| `/cosmetics help` | the parts you may use | everyone who sees the command |

`CosmeticSubCommands` decides what is visible (tested in `CosmeticSubCommandsTest` and `PlayArgumentsTest`): a part that is switched off or not permitted is missing from help, tab completion and the menu, typing it shows the help. `play` is a tool for consoles and game plugins, so it is never listed in the help or offered as the first word; once typed by someone with the permission, tab completion offers the players on this server, the effect categories, `here` and `~`. Names of `give` and `take` resolve like every PRISMARIN command (`PlayerService.resolve`), tab completion offers categories, parts, players and cosmetic ids.

`/cosmetics play` takes a player on this server (name or UUID) and an effect category of the catalogue (`kill-effect`, `win-effect` or one of a plugin). Without a spot the effect plays at the player and runs effects follow him; `here` plays where the sender stands (a player or a command block, from the console at the player); `x y z` are coordinates in the player's world, `~` and `~2` count from the player. It plays only what he wears there, with the usual rules (category active on this server, not vanished, viewers who hide others or cannot see him see nothing) and fires `CosmeticEffectEvent` with `COMMAND`. Replies: `cosmetics.played`, `cosmetics.play-none`, `cosmetics.play-offline`, `cosmetics.play-slot`, `cosmetics.usage-play`.

#### Win effects without code

Most minigame plugins can run commands when a game ends or a player wins, usually as console commands in their config or a rewards file. Point them at `/cosmetics play` with that plugin's player placeholder:

```yaml
# The keys differ per plugin, look for "commands", "rewards" or "on win" in its config
win-commands:
  - "cosmetics play {player} win-effect"
  - "cosmetics play %player% win-effect ~ ~3 ~"
final-kill-commands:
  - "cosmetics play {player} kill-effect"
```

Use the form the plugin expects (with or without `/`, with a `console:` prefix and so on) and its own placeholder (`{player}`, `%player%`, `<player>`, `%winner%`). A kill effect through a command plays at the killer, because plugins rarely hand out the victim's position; switch the automatic kill trigger off when the plugin plays kill effects itself. Command blocks can use `here`, for example `cosmetics play Steve win-effect here` on a podium.

### The menu

`CosmeticMenus`, built on `MenuService` and `MenuStyle`. The main menu shows the collection (owned of all, a progress bar), one button per category with stained glass in the category's `pane` colour above and below it (six categories leave the middle column free), the balance of every currency the catalogue uses, "take everything off", close, back (when opened from the lobby profile) and the viewer setting. A category opens paged, 28 cosmetics per page with as many pages as needed and previous and next arrows, with the category colour on the top and bottom row. Each item shows its name in the rarity colour, the rarity, the description, the state (worn, owned, unlocked with the rank, price in the currency's colour and what is missing, only with a rank, only as a reward) and what a click does. Worn cosmetics glow. Left click puts on, takes off or opens the purchase confirmation (lime buy panes, red cancel panes, the balance); with `equip-after-purchase` a bought cosmetic is put on right away. Right click on a particle cosmetic shows a preview for `preview-seconds` in front of the player, only to him: trails and auras draw where he looks, projectile trails fly along an arc, kill and win effects play in full with their entities (only he sees them) and again after a short pause. Cosmetics with `hidden: true` show up only once owned.

### Particles, visibility and performance

- `ParticleTicker` runs one main thread task every `performance.interval-ticks`, and only while at least one player on this server wears a trail or an aura of an active category or a preview runs. Nothing equipped means no task.
- Per frame it sends at most `performance.max-particles` particles (points times viewers) for all players together. The player who did not get his turn goes first next frame, so a full lobby slows the frames down instead of the server. Projectile trails, kill and win effects share one budget of `max-particles` per tick (`ParticleBudget`); a full tick skips the particles of a frame, not the effect, and the one that waited goes first next tick.
- Particles go only to players within `performance.view-distance` blocks who may see the wearer (`ViewRules`, tested in `ViewRulesTest`): `Player.canSee` covers vanish, the lobby player hider and the silent lobby; viewers with "hide others" on see only their own particles and no effects of others; filters of plugins can hide more. Vanished, invisible, dead and spectating wearers send nothing, vanished and spectating ones play no effects and start no trails.
- `ParticleSpawner` maps `dust` (with colour and size), `dust_color_transition`, `entity_effect`, block particles (`block`, `falling_dust`, `block_marker`, `dust_pillar`, with `variant` as the block) and `item` (with `variant` as the item) and every particle without data. Particles that need other data are reported at start. The shapes are pure functions in `:shared` (`CosmeticShapes`, tested in `CosmeticShapesTest` and `EffectShapesTest`).

### Effects

Kill and win effects (and every effect slot of a plugin) are runs of `PaperEffects`: they start in the tick they are played, get one frame per tick for `duration` ticks and end on their own. `EffectScheduler` in `:shared` (tested in `EffectSchedulerTest`) holds at most `performance.max-effects` runs at once, refuses more, rotates the order every tick, ends a failing run and only has a task while a run is going; an effect of one frame never starts a task. Runs end and clean up when the server or the module stops, their player quits, their world unloads or their effect kind is unregistered; `stopEffects(player)` ends the runs of one player.

`kind` decides what a run does besides its particles:

| Kind | What it does | Parameters |
| --- | --- | --- |
| `particles` (default) | draws the shape, every second tick while it runs | `duration` |
| `lightning` | a visual lightning bolt (`strikeLightningEffect`) where the effect plays, plus the shape | at least 10 ticks |
| `lightning-storm` | `points` bolts within `radius` blocks over the duration | `points` (6), `radius` (5) |
| `squid` | a squid rockets up and pops into the particle burst of the shape | at least 20 ticks |
| `fountain` | `points` items of `variant` (default `cookie`) jump out and fall | `points` (20), `variant` |
| `tombstone` | a block display of `variant` (default `chiseled_stone_bricks`) on a dirt mound rises and sinks back | at least 40 ticks |
| `firework` | `points` (1 to 5) fireworks of type `variant` (`ball`, `ball_large`, `star`, `burst`, `creeper`) burst at once in `colors` | |
| `fireworks` | `points` rockets of random types and two of `colors` each launch within `radius` over the duration | `points` (8), `radius` (3) |
| `totem` | the totem animation of the player (`sendEntityEffect`), on his own screen too, plus the shape | |
| `sheep` | `points` (up to 16) coloured sheep hop around the spot to note block notes, change colour and vanish in a puff | `points` (6), `radius` (2.4) |

The new shapes for effects: `wave` (a ring that grows and starts again every second), `rings` (three rings rising in turn, one colour each), `meteor` (`points` meteors with a tail falling towards the middle, first colour head, second tail), `rain` (confetti or rain falling in a cylinder of `radius`), `swirl` (three arms turning flat on the ground), `ghost` (a small ghost that leaves the body and floats up, second colour for the eyes) and `none` (only the kind). For auras: `crown` (spikes in the first colour over the head), `gyro` (two tilted rings orbiting the body) and `rain-cloud` (a cloud in the first colour over the head with drops in the second). Effects played without a spot (`playEffect(player, slot)`, `/cosmetics play` without coordinates) follow the player while they run, effects at a spot stay there.

Effects never change the game:

- Entities of effects (squids, sheep, items, fireworks, block displays, lightning) are spawned invisible for everyone (`setVisibleByDefault(false)`) and shown only to the players who may see the effect, not persistent, invulnerable, silent (the kind plays its own sounds to the viewers), mobs without AI (`setAware(false)`), not collidable and unable to pick anything up. They carry the PDC key `prismarincore:cosmetic-effect`, sit in `EffectEntityRegistry` (tested in `EffectEntityRegistryTest`, at most `performance.max-effect-entities` at once) and are removed when their run ends, their chunk unloads or the server stops; one that is ever loaded from a chunk again is removed at once.
- `EffectEntities` cancels for them: all damage to them (but void and `/kill`) and all damage they deal (so real fireworks hurt and push nobody), burning, being targeted by mobs and targeting, block changes (sheep eating grass), trampling farmland and pressure plates, vibrations for sculk sensors (also of the lightning), pickups by players, mobs and hoppers, merging items, dropping items, shearing, leashing, right clicks, vehicles and portals. A death drops nothing. A spawn that another plugin or the lobby protection cancelled is allowed again for them.
- Game plugins that count mobs or entities skip them with `CosmeticApi.effectEntity(uuid)` or `cosmetics.effectEntity(uuid)`, or by the PDC key.
- Lightning is visual only and starts in the air block at the victim's feet, so it never hits a lightning rod or copper; where that block is not free a particle bolt with thunder is drawn instead.

### Hats

`HatService` puts the hat on the head slot when the category is active and remembers the real helmet, also inside the hat item (`cosmetic-real-helmet`), so a crash cannot lose it. The real helmet comes back when the hat is taken off, the player leaves or the server stops; a hat left over after a crash is replaced by the stored helmet on the next join. The hat cannot be clicked, moved, swapped, dropped or placed and is never dropped on death (the real helmet is). When a game plugin or `/hat` puts something else on the head, that wins and the stored helmet goes into the inventory. Damageable hat items are unbreakable.

Hats never change the game (`HatSafety`, tested in `HatSafetyTest`): every hat item gets a single zero armor modifier, which replaces all default attribute modifiers of the item (armor, toughness, knockback resistance), the attribute lines are hidden and enchantments removed. `turtle_helmet` is refused because it gives water breathing; `carved_pumpkin` is worn as `jack_o_lantern` because it blurs the screen and calms endermen; golden armor is worn as a neutral item with the golden helmet's model and equipment look, so piglins do not turn friendly. `zombie_head`, `skeleton_skull`, `creeper_head` and `piglin_head` are reported at start, they make those mobs notice the wearer later; the default catalogue uses none of them. The dragon head's jaw moves while the wearer walks, it plays no sound when worn.

### `cosmetics.yml`

Written with comments on the first start, missing keys are added on every start, mistakes are logged and the cosmetic is skipped or the default is used. Keep it the same on every server. `/prismarin reload` reloads it (the part `cosmetics`) and refreshes every player.

| Key | Default | Meaning |
| --- | --- | --- |
| `active-groups` | `[lobby]` | groups or server ids where categories without own `groups` show |
| `equip-after-purchase` | `true` | put a bought cosmetic on right away |
| `preview-seconds` | `5` | 0 to 30, 0 turns the preview off |
| `sub-commands.hide` | `true` | `false` removes `/cosmetics hide` and the menu button |
| `lobby.profile-entry` | `false` | extra entry in the lobby profile menu (the hotbar item is the default way) |
| `performance.interval-ticks` | `3` | 1 to 20 |
| `performance.max-particles` | `1500` | 50 to 20000 per frame of trails and auras, and per tick of projectile trails and effects |
| `performance.view-distance` | `24` | 4 to 96 blocks |
| `performance.max-effects` | `32` | 1 to 500 effects running at once |
| `performance.max-effect-entities` | `120` | 0 to 2000 effect entities at once |
| `triggers.projectile-trail.auto` | `true` | trails follow projectiles by themselves |
| `triggers.projectile-trail.projectiles` | `[arrow, spectral_arrow, trident, snowball, egg, ender_pearl]` | entity types that get the trail |
| `triggers.projectile-trail.interval-ticks` | `1` | 1 to 10 |
| `triggers.projectile-trail.max-ticks` | `160` | 20 to 1200 ticks of flight |
| `triggers.projectile-trail.max-tracked` | `200` | 1 to 2000 projectiles at once |
| `triggers.kill-effect.auto` | `true` | kill effects play by themselves on player kills |
| `triggers.kill-effect.mob-kills` | `false` | also on mob kills |
| `rarities.<id>` | `common`, `uncommon`, `rare`, `epic`, `legendary` | `name` (text, message key or per language), `color` (`#RRGGBB`), `order` |
| `categories.<slot>` | the six built in slots | `enabled`, `name`, `icon`, `pane` (glass colour in the menu), `order`, `groups` |
| `catalogue-version` | `2` | how far the default catalogue was added to this file, see below |
| `cosmetics.<id>` | 81 cosmetics, see below | `category`, `name`, `description`, `rarity`, `icon`, `price`, `currency` (default `coins`), `permission`, `hidden`, `order`, for hats `item` or `texture` (texture value, URL or hash), for particles `effect.particle`, `shape`, `colors`, `size`, `count`, `spread`, `speed`, `radius`, `height`, `points`, `spin`, `sound`, `kind`, `duration` (0 to 200 ticks), `variant` |

A price in a currency that is not in `economy.currencies` is reported and the cosmetic is not for sale. Without a price a cosmetic comes from its permission, rewards (`custom: {cosmetic: <id>}` in `daily-rewards.yml` or the proxy's `votes.yml`), `/cosmetics give` or plugins.

**New default cosmetics in existing files.** The default catalogue is versioned. A file without `cosmetics` gets the whole catalogue and `catalogue-version: 2`. A file that has `cosmetics` but no `catalogue-version` was written by PRISMARIN 1.0 and counts as version 1: on the first start of this version every cosmetic of version 2 whose id is not in the file yet is added (ids the operator already uses for something else are left as they are), then `catalogue-version` becomes 2. Cosmetics of version 1 that an operator removed are not brought back, cosmetics he changed are not touched, and a cosmetic of version 2 he removes after that stays removed, because each version is only added once. A higher `catalogue-version` than this build knows is left alone. Tested in `CosmeticSettingsTest`.

Default catalogue:

| Category | Cosmetics |
| --- | --- |
| trail | `flame-trail` (common, 500 coins), `heart-trail` (uncommon, 1000 coins), `note-trail` (uncommon, 1000 coins), `rainbow-trail` (rare, 2500 coins, six dust colours), `soul-trail` (rare, 25 gems), `cherry-trail` (epic, 4000 coins), `streak-flame` (epic, reward of the 30 day login streak) |
| aura | `ring-aura` (common, 750 coins), `snow-cloud` (uncommon, 1500 coins), `frost-halo` (uncommon, 1600 coins), `light-spiral` (rare, 3000 coins), `heart-circle` (rare, 2200 coins), `rain-cloud` (rare, 25 gems), `spark-orbit` (epic, 40 gems), `ender-rings` (epic, 4500 coins), `flame-crown` (epic, 45 gems), `angel-wings` (legendary, 80 gems), `ember-wings` (legendary, 80 gems), `vip-halo` (epic, `prismarin.cosmetics.vip`), `team-wings` (legendary, `prismarin.cosmetics.team`) |
| hat | `pumpkin-head` (common, 600 coins), `melon-head` (common, 600 coins), `glowstone-head` (common, 700 coins), `astronaut` (uncommon, 1500 coins), `sea-lantern` (uncommon, 1200 coins), `slime-head` (uncommon, 1400 coins), `honey-head` (uncommon, 1400 coins), `tnt-head` (rare, 3000 coins), `birthday-cake` (rare, 20 gems), `ice-cube` (rare, 2000 coins), `tinted-visor` (rare, 15 gems), `jukebox-head` (rare, 2500 coins), `soul-lantern` (rare, reward only), `germany-fan` (epic, 30 gems, a head by texture value), `amethyst-crown` (epic, 5000 coins), `wither-skull` (epic, 40 gems), `blossom-crown` (epic, reward only), `dragon-head` (legendary, 90 gems), `vip-crown` (legendary, `prismarin.cosmetics.vip`), `team-beacon` (legendary, `prismarin.cosmetics.team`) |
| projectile-trail | `smoke-arrows` (common, 500 coins), `drip-arrows` (common, 500 coins), `flame-arrows` (common, 600 coins), `snow-arrows` (common, 600 coins), `crit-arrows` (uncommon, 900 coins), `heart-arrows` (uncommon, 1000 coins), `note-arrows` (uncommon, 1000 coins), `spark-arrows` (uncommon, 1200 coins), `ender-arrows` (uncommon, 1200 coins), `glyph-arrows` (rare, 2000 coins), `rainbow-arrows` (rare, 2500 coins), `soul-arrows` (rare, 20 gems), `cherry-arrows` (epic, 3500 coins), `totem-arrows` (epic, 30 gems), `comet-arrows` (legendary, 60 gems, a spinning ring) |
| kill-effect | `rainbow-ring` (common, 800 coins, `wave`), `explosion-kill` (uncommon, 1000 coins, particles and sound only), `blood-burst` (uncommon, 1200 coins, redstone block particles), `soul-rise` (uncommon, 1500 coins), `heart-burst` (uncommon, 1500 coins), `ice-shatter` (rare, 2200 coins, packed ice particles), `flame-pillar` (rare, 2500 coins), `ghost-rise` (rare, 2800 coins, `ghost`), `cookie-fountain` (rare, 3000 coins, `fountain`), `firework-kill` (rare, 20 gems), `lightning-kill` (rare, 25 gems, `lightning`), `tombstone` (epic, 4000 coins, `tombstone`), `firework-star` (epic, 30 gems, `firework` star), `squid-launch` (epic, 35 gems, `squid`), `gold-fountain` (legendary, 60 gems, `fountain` of gold ingots), `firework-creeper` (legendary, 50 gems, `firework` creeper) |
| win-effect | `winner-halo` (uncommon, 2500 coins, follows the winner), `confetti-rain` (rare, 4500 coins, `rain`), `totem-pop` (rare, 30 gems, `totem`), `rainbow-rings` (epic, 6000 coins, `rings`), `golden-pillar` (epic, 50 gems), `dragon-breath` (epic, 50 gems, `swirl`), `sheep-disco` (epic, 60 gems, `sheep`), `fireworks-show` (legendary, 100 gems, `fireworks`, ten rockets), `meteor-shower` (legendary, 90 gems, `meteor`), `lightning-storm` (legendary, 80 gems, `lightning-storm`) |

### Purchases

`CosmeticActions` in `:shared` (tested in `CosmeticActionsTest`): the main thread fires `CosmeticPurchaseEvent` after checking that the player does not own the cosmetic (also not through a permission), then `EconomyService.withdraw` takes the price atomically, then one `DocumentStore.update` stores the cosmetic. When the storage fails, or another server stored it first, the money is paid back and the player reads `failed` or `already-owned`. Twelve parallel purchases of the same cosmetic pay exactly once in the test.

### PlaceholderAPI

| Placeholder | Value |
| --- | --- |
| `%prismarin_cosmetics_owned%`, `_total`, `_percent` | owned cosmetics of active categories (with permission ones), all that are not hidden, the share |
| `%prismarin_cosmetics_hidden%` | `true` when the player hides the particles of others |
| `%prismarin_cosmetic_<slot>%` | name of the worn cosmetic of that slot, for example `%prismarin_cosmetic_aura%` or `%prismarin_cosmetic_kill-effect%`; empty when none |
| `%prismarin_cosmetic_<slot>_id%`, `_rarity` | its id and its rarity name |

Resolved by `CosmeticPlaceholders` from the cache (tested in `CosmeticPlaceholdersTest`).

### Texts

Under `cosmetics.*` in the PrismarinCore message files: `category.<slot>`, `rarity.<id>`, `price.<currency>` and `wallet.<currency>` (with `.currency` as fallback, so a new currency still shows), `hat-lore`, the results `purchased`, `already-owned`, `not-enough-money` (`%missing%`), `not-for-sale`, `locked-rank`, `locked-reward`, `equipped`, `equipped-elsewhere`, `already-equipped`, `unequipped`, `unequipped-all`, `nothing-equipped`, `nothing-equipped-all`, `not-owned`, `category-off`, `cancelled`, `failed`, `unknown`, `preview`, `hide-on`, `hide-off`, `unlocked`, `unlocked-owned`, `given`, `given-already`, `taken`, `taken-none`, `usage-give`, `usage-take`, `usage-play`, `played` (`%cosmetic%`, `%player%`), `play-none` (`%player%`, `%category%`), `play-offline`, `play-slot` (`%category%`, `%categories%`), `help-*` and `menu.*`. `%cosmetic%` is the name in the rarity colour, `%price%` the price in the currency's colour. The names and descriptions of the default cosmetics stand in `cosmetics.yml`, in English and German. `reward.cosmetic` names a cosmetic in reward summaries, `lobby.item.cosmetics.*` is the lobby hotbar item. `CosmeticMessages` maps every result to its text (tested with every status in `CosmeticMessagesTest`).

Classes: `cosmetics/PaperCosmetics`, `CosmeticSettings`, `CosmeticMenus`, `CosmeticsCommand`, `CosmeticSubCommands`, `PlayArguments`, `CosmeticMessages`, `CosmeticTexts`, `CosmeticLayout`, `CosmeticPlaceholders`, `CosmeticReward`, `ParticleTicker`, `ParticleSpawner`, `HatService`, `AutoTriggers`, `PaperEffects`, `EffectRun`, `BuiltInEffects`, `EffectEntities`; in `:shared` `CosmeticCatalog`, `CosmeticCategory`, `CosmeticLedger`, `CosmeticActions`, `CosmeticShapes`, `ActiveSlots`, `ViewRules`, `HatTextures`, `HatSafety`, `EffectScheduler`, `ProjectileTrails`, `ParticleBudget`, `TickClock`, `KillTrigger`, `EffectEntityRegistry`. Tests: `CosmeticSettingsTest`, `CosmeticSubCommandsTest`, `PlayArgumentsTest`, `CosmeticPlaceholdersTest`, `CosmeticRewardTest`, `CosmeticMessagesTest`, `CosmeticLayoutTest` and the shared tests (`EffectSchedulerTest`, `ProjectileTrailsTest`, `KillTriggerTest`, `EffectEntityRegistryTest`, `HatSafetyTest`, `EffectShapesTest` among them).
