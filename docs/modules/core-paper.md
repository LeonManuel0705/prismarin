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
