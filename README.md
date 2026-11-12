# PRISMARIN

A framework for Minecraft networks on Paper and Velocity. Profiles, currencies, localisation, events, a cross server message bus and a server directory live in one shared layer, and a network plugs its own game servers in behind it, whether that is an existing game plugin or a mode written from scratch.

The point of the architecture is that a game mode never talks to a database. It asks the profile service for a player, the economy service for a balance, or the message bus to reach another server. The implementation behind those interfaces can be JSON files or SQLite on a laptop, or MySQL, MariaDB, MongoDB and Redis across a real network, without a single line changing in the game mode.

## What a network gets

| Area | What it does |
|:---|:---|
| Profiles | One profile per player across every server, with locale, level, settings and balances |
| Economy | Any number of currencies the network defines itself, with atomic transactions on every backend |
| Branding | Name, colours and chat prefix come from the config, every bundled text uses `%brand%` and `%brand_prefix%`, confirmations and errors use `%success_prefix%` (light green) and `%error_prefix%` (light red) |
| Text | MiniMessage, `&` colour codes, `&#RRGGBB` and `&x&R&R&G&G&B&B` hex, placeholders written as `%name%` |
| Servers | A directory with groups and properties per server, used by the navigator and by plugins |
| Game servers | Game states (waiting, starting, running, ending, full) from the API or from a placeholder of any game plugin, visible to lobbies even without Redis, plus `/play <group>` and `connectToGroup` that pick the best free server |
| Server queue | Full servers and groups, and servers with a running round, put players into a queue on the proxy instead of an error: priority levels by permission, first come first served, parties that queue together and go in together, a reconnect grace time, action bar or boss bar with the place, `/queue` for players and pause, clear and send for staff, all from `queue.yml` and the API |
| Admin | Staff chat, staff list, `/find`, `/send`, `/goto`, bans, temporary bans, IP bans, mutes, kicks and warnings with templates that escalate on repeat offences, a punish menu, `/check` and history, reports, broadcasts, maintenance per server or group, player records, an audit log, outage alerts with Discord webhook, `/netexec`, a SuperVanish style vanish with silent containers, and `/prismarin migrate` to move all data between storage backends |
| Essentials | `/tpall`, `/tphere`, `/heal`, `/feed`, `/fly`, `/gm`, `/speed`, `/clearinventory`, `/invsee`, `/enderchest`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/ping`, `/hat` and `/skull`, each with its own permission and switch, stepping aside when EssentialsX or CMI is installed |
| Reports | `/report` opens a reason menu, bundles reports against the same player into one case, attaches the recent chat as evidence, shows staff the client, ping and record with one click actions, closes cases when a punishment is issued and thanks the reporters, also for offline players |
| Nicks | A cross server nick system that changes the name and the skin, with a fake rank that keeps real permissions, auto nick, rules per server group, per round and per world |
| Friends and parties | Friend requests, online status and jump, parties with chat and follow the leader, network wide `/msg` and `/r`, all reachable from the lobby profile menu |
| JoinMe | `/joinme` sends a clickable invite with the creator's skin face drawn in chat to the lobbies, the whole network or only friends, party and clan; tiers for cooldown, cost, lifetime and joins by permission; a click brings the player to the creator's server and world, or a game plugin's handler puts him into the arena or team; works without Redis |
| Clans | Persistent clans across the network with member, moderator, co-leader and leader, a coloured tag next to the name in chat, tab list and name tag, clan chat, a lobby menu and a full API for plugins such as clan wars |
| Cosmetics | Particle trails, auras and wings, hats from any item or head texture, arrow trails and kill effects that play by themselves, win effects through one command for any minigame plugin, effects with real fireworks, lightning, squids and sheep that never touch the game; bought with the network's currencies in a colourful paged menu with preview and confirmation, unlocked by permission, daily rewards, votes or the API; network wide ownership, particles only for players who may see the wearer and on one task that only runs while someone wears them |
| Ranks and levels | `/rank` sets, adds or removes LuckPerms groups with optional durations, LuckPerms prefixes with `&` codes colour the name, `/level` sets, adds or removes levels and XP, a balance command per currency plus `/balance` |
| Network overview | `/network` opens a live inventory with status, players, ping, version, TPS, memory and uptime per server |
| Events | Profiles, balances, server switches, nicks, punishments, reports, friends, parties, clans, maintenance and staff alerts, listed in `docs/api.md` |
| Plugin API | One interface for every service, storage for other plugins, audit log, maintenance and staff alerts reachable from backends, see `docs/api.md` |
| Vault | Registers as the Vault economy for the classic API and the multi currency VaultUnlocked API, so every shop or game plugin pays out network currency |
| PlaceholderAPI | `%prismarin_balance%`, `%prismarin_group_online_bedwars%`, `%prismarin_rank_prefix%`, `%prismarin_language%`, `%prismarin_data_<key>%` for attributes of other plugins and more, and PlaceholderAPI placeholders inside PRISMARIN's own tablist, sidebar and chat |
| Compatibility | Switches its own tablist, sidebar or chat format off when TAB, a scoreboard plugin or a chat plugin is installed |
| Systems and modules | Whole systems (economy, levels, network status) and single features (chat, tablist, sidebar, join messages, commands, proxy features, lobby parts) can each be switched off |
| Lobby | An optional ready made lobby with navigator, lobby selector for several lobby worlds or servers with premium lobbies, profile menu, language menu, nick tool, silent lobby, player hider, double jump, void teleport and custom hotbar items with click actions |

## Compatibility

| Part | Supported |
|:---|:---|
| Backends | Paper 1.21.7 to 1.21.11 and forks based on it, tested live on 1.21.11. Spigot, Bukkit and Folia are not supported. |
| Proxy | Velocity 3.4 |
| Java | 21 or newer |
| Clients | Whatever the proxy accepts. With ViaVersion, ViaBackwards and ViaRewind 1.21 and 1.16.5 clients were tested with bots; 1.8 clients need worlds that stay between Y 0 and 255. |
| Older backends | A 1.8 or 1.16 server can still run behind the proxy and gets every proxy feature (bans, mutes, reports as clickable chat, friends, parties, private messages, staff chat, maintenance), only the backend features such as menus, nicks, vanish and sidebar need PRISMARIN on the server itself. |
| Optional | MySQL 5.7+, MariaDB, MongoDB, SQLite, Redis or a Redis compatible server, LuckPerms, Vault or VaultUnlocked, PlaceholderAPI |

## Architecture

```
        api          public contracts, zero platform dependencies
         |
       shared        storage, message bus, config, locale, text, status
        /   \
core-velocity  core-paper
    proxy         backends
                     |
                 lobby-paper   optional
```

| Module | Purpose |
|:---|:---|
| `:api` | The contract for other plugins. `Prismarin`, players (names, nicks, language, server, world, viewer rules and an attribute registry), profiles, economy, events, locale, messaging, server directory, status, game states, the connector and punishments. |
| `:shared` | The implementations. Config loading, JSON, SQLite, MySQL/MariaDB and MongoDB storage, local and Redis message bus, YAML locales, the text formatter, server list ping, heartbeats, server selection, punishments, reports and the audit log. |
| `:core-paper` | Core plugin for Paper backends. Chat format with rank prefixes, tablist, sidebar and menu frameworks, economy commands, network overview. |
| `:core-velocity` | Core plugin for the proxy. MOTD, maintenance mode, `/hub`, `/sendall`, lobby fallback when a backend drops a player. |
| `:lobby-paper` | Optional lobby gameplay on top of the core. |
| `:example-paper` | An example plugin that uses only the API, built but not shipped. |

## Using the API

The API is published as `net.prismarin:prismarin-api` (GitHub Packages) and through JitPack, both as a small jar with a sources jar. Add it as `compileOnly`, PrismarinCore provides it at runtime:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.LeonManuel0705:prismarin:v1.1.0")
}
```

For GitHub Packages use `https://maven.pkg.github.com/LeonManuel0705/prismarin` with a token that has `read:packages` and `compileOnly("net.prismarin:prismarin-api:1.0.0")`; `./gradlew :api:publishToMavenLocal` puts the same coordinates into `mavenLocal()`.

Add `depend: [PrismarinCore]` to your `plugin.yml` (on Velocity a dependency on `prismarin-core`), then:

```java
Prismarin prismarin = PrismarinProvider.get();

Currency coins = prismarin.economy().primaryCurrency();
prismarin.economy().deposit(player.getUniqueId(), coins, 50);

prismarin.events().subscribe(BalanceChangedEvent.class,
        event -> getLogger().info(event.uuid() + " now has " + event.newBalance()));

List<NetworkServer> bedwars = prismarin.servers().byGroup("bedwars");
```

Everything PRISMARIN knows about a player sits behind `players()`, with nicks, vanish and the chosen language already applied, so a game plugin does not rebuild any of it:

```java
PlayerService players = prismarin.players();

String shown = players.displayName(viewer.getUniqueId(), target.getUniqueId());
Locale language = players.locale(target.getUniqueId());
players.online(target.getUniqueId()).ifPresent(player -> log(player.server() + " / " + player.world()));
players.resolve(viewer.getUniqueId(), typedName).thenAccept(found -> { });
players.find(friendUuid).thenAccept(found -> { });

prismarin.events().subscribe(ServerChangedEvent.class, event -> { });
players.attributes().register(PlayerAttribute.of("bedwars_wins", player -> wins(player.uuid())));
```

A game plugin keeps its own data in PRISMARIN's storage, and an anticheat on a backend reaches the proxy without any channel code of its own:

```java
TypedCollection<Stats> stats = prismarin.storage().namespace("bedwars").collection("stats", Stats.class);
stats.save(uuid.toString(), new Stats(12, 30));

prismarin.audit().record("AntiCheat", "anticheat.flag", suspectName, "Reach 4.2");
prismarin.notifications().notifyStaff(StaffAlert.of("AntiCheat",
        NoticeText.of("%player% may use Reach").with("player", suspectName)));
prismarin.reports().ifPresent(reports -> reports.file(anticheatId, "AntiCheat", suspect, "hacking Reach 4.2"));
prismarin.punishmentTemplates().ifPresent(templates -> templates.punish(suspect, "hacking", "AntiCheat"));
prismarin.maintenance().ifPresent(maintenance -> maintenance.setServer("bw-1", true, "BedwarsReset"));
```

`displayName` shows the real name only to the player himself and to staff with `prismarin.nick.realname`, `resolve` finds a player by what someone typed without leaking a real name. Registered attributes show up as `%prismarin_data_<key>%` and in the lobby profile.

Every service, how to get it on Paper and Velocity, the events and the limits are listed in [`docs/api.md`](docs/api.md). The module `example-paper` is a complete plugin that uses the API in small classes; it is built with the project but not shipped in `dist`.

## Configuration

Every server writes a `config.yml` on first start and adds missing sections on later starts, so an update never needs a manual merge.

```yaml
server-id: lobby
storage:
  mode: LOCAL
branding:
  name: PRISMARIN
  primary-color: "#22D3EE"
  accent-color: "#2DD4BF"
  prefix: "%brand% <dark_gray>|</dark_gray> <gray>"
economy:
  currencies: [coins, gems]
  primary: coins
systems:
  economy: true
  levels: true
  network-status: true
modules:
  chat: true
  tablist: true
```

A network that keeps its own coin plugin sets `systems.economy: false`. Commands, displays and transactions disappear, and `prismarin.systemEnabled("economy")` tells other plugins about it.

`storage.mode` decides where profiles, balances and documents live:

| Mode | Backend | Shared across servers |
|:---|:---|:---|
| `LOCAL` | JSON files in the plugin folder | no |
| `SQLITE` | One SQLite file, `storage.sqlite.file` (default `prismarin.db`, relative to the plugin folder) | no |
| `MONGO` | MongoDB, `storage.mongo.uri` and `storage.mongo.database` | yes |
| `MYSQL` or `MARIADB` | MySQL 5.7 or newer, or MariaDB, through MariaDB Connector/J and a HikariCP pool | yes |

`LOCAL` and `SQLITE` make a single server usable with no infrastructure at all, the message bus then runs in process. Drivers, the Redis client, Guice and Configurate are downloaded from Maven Central on first start, with checksums checked, and only what the configuration uses (Paper through `libraries:` in `plugin.yml`, the proxy into `plugins/prismarin-core/libraries/`). That keeps all three jars at about 4 MB together. `MONGO` or `MYSQL` plus `redis.enabled: true` turns the same jars into a network where profiles, messages and heartbeats are shared across every server.

A MySQL or MariaDB network sets the connection under `storage.mysql`:

```yaml
storage:
  mode: MYSQL
  mysql:
    host: db.example.net
    port: 3306
    database: prismarin
    username: prismarin
    password: "change me"
    pool-size: 8
    properties:
      sslMode: trust
```

The database has to exist, the tables `prismarin_profiles`, `prismarin_balances` and `prismarin_documents` are created on startup. `pool-size` is the number of connections each server keeps open. `properties` passes options straight to the driver. The driver verifies TLS certificates by default, so a server with a self signed certificate needs `sslMode: trust`, and a server without TLS needs `sslMode: disable` (MySQL 8 then also wants `allowPublicKeyRetrieval: true`).

Servers are listed in `servers.yml` with a group and any extra properties:

```yaml
servers:
  bw-1:
    display-name: "&cBedwars 1"
    host: 127.0.0.1
    port: 25570
    group: bedwars
    icon: RED_BED
```

## Testing

More than 700 unit tests run on every build. 5 more classes are integration tests, tagged separately, that spin up real MongoDB, Redis, MySQL and MariaDB containers through Testcontainers.

```bash
./gradlew build             # compile plus unit tests
./gradlew integrationTest   # Testcontainers backed integration suite
./gradlew dist              # collect the plugin jars in dist/
```

## Status

Version 1.0.0 is the first stable release. On top of the 0.x series it brings friends, parties with roles and private messages, a cross server nick system with skins and fake ranks, reports with a staff menu, chat evidence and claims, punishment templates that escalate, ban and mute lists, a staff mode, an essentials module, a reworked lobby with several lobbies and custom items, LuckPerms display names, and a public API for every system: players, levels, vanish, staff mode, storage for other plugins, audit log, maintenance, staff notifications, sidebar, tab list, chat, menus and the lobby. See `docs/api.md` and the example plugin in `example-paper`. Next on the roadmap is publishing on Hangar and Modrinth.

## License

MIT
