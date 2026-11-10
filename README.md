# PRISMARIN

A framework for Minecraft networks on Paper and Velocity. Profiles, currencies, localisation, events, a cross server message bus and a server directory live in one shared layer, and a network plugs its own game servers in behind it, whether that is an existing game plugin or a mode written from scratch.

The point of the architecture is that a game mode never talks to a database. It asks the profile service for a player, the economy service for a balance, or the message bus to reach another server. The implementation behind those interfaces can be JSON files or SQLite on a laptop, or MySQL, MariaDB, MongoDB and Redis across a real network, without a single line changing in the game mode.

## What a network gets

| Area | What it does |
|:---|:---|
| Profiles | One profile per player across every server, with locale, level, settings and balances |
| Economy | Any number of currencies the network defines itself, with atomic transactions on every backend |
| Branding | Name, colours and chat prefix come from the config, every bundled text uses `%brand%` and `%brand_prefix%` |
| Text | MiniMessage, `&` colour codes, `&#RRGGBB` and `&x&R&R&G&G&B&B` hex, placeholders written as `%name%` |
| Servers | A directory with groups and properties per server, used by the navigator and by plugins |
| Game servers | Game states (waiting, starting, running, ending, full) from the API or from a placeholder of any game plugin, visible to lobbies even without Redis, plus `/play <group>` and `connectToGroup` that pick the best free server |
| Admin | Staff chat, staff list, `/find`, `/send`, `/goto`, bans, temporary bans, IP bans, mutes, kicks and warnings with templates that escalate on repeat offences, a punish menu, `/check` and history, reports, broadcasts, maintenance per server or group, player records, an audit log, outage alerts with Discord webhook, `/netexec`, a SuperVanish style vanish with silent containers, and `/prismarin migrate` to move all data between storage backends |
| Essentials | `/tpall`, `/tphere`, `/heal`, `/feed`, `/fly`, `/gm`, `/speed`, `/clearinventory`, `/invsee`, `/enderchest`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/ping`, `/hat` and `/skull`, each with its own permission and switch, stepping aside when EssentialsX or CMI is installed |
| Reports | `/report` opens a reason menu, bundles reports against the same player into one case, attaches the recent chat as evidence, shows staff the client, ping and record with one click actions, closes cases when a punishment is issued and thanks the reporters, also for offline players |
| Nicks | A cross server nick system that changes the name and the skin, with a fake rank that keeps real permissions, auto nick, rules per server group, per round and per world |
| Friends and parties | Friend requests, online status and jump, parties with chat and follow the leader, network wide `/msg` and `/r`, all reachable from the lobby profile menu |
| Ranks and levels | `/rank` sets, adds or removes LuckPerms groups with optional durations, LuckPerms prefixes with `&` codes colour the name, `/level` sets, adds or removes levels and XP, a balance command per currency plus `/balance` |
| Network overview | `/network` opens a live inventory with status, players, ping, version, TPS, memory and uptime per server |
| Events | `ProfileLoadedEvent`, `ProfileUnloadedEvent`, `BalanceChangedEvent`, `PunishmentIssuedEvent` and `PunishmentRevokedEvent` on proxy and backends |
| Vault | Registers as the Vault economy for the classic API and the multi currency VaultUnlocked API, so every shop or game plugin pays out network currency |
| PlaceholderAPI | `%prismarin_balance%`, `%prismarin_group_online_bedwars%` and more for other plugins, and PlaceholderAPI placeholders inside PRISMARIN's own tablist, sidebar and chat |
| Compatibility | Switches its own tablist, sidebar or chat format off when TAB, a scoreboard plugin or a chat plugin is installed |
| Systems and modules | Whole systems (economy, levels, network status) and single features (chat, tablist, sidebar, join messages, commands, proxy features, lobby parts) can each be switched off |
| Lobby | An optional ready made lobby with navigator, lobby selector for several lobby worlds or servers with premium lobbies, profile menu, language menu, nick tool, silent lobby, player hider, double jump, void teleport and custom hotbar items with click actions |

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
| `:api` | The contract for other plugins. `Prismarin`, profiles, economy, events, locale, messaging, server directory, status, game states, the connector and punishments. |
| `:shared` | The implementations. Config loading, JSON, SQLite, MySQL/MariaDB and MongoDB storage, local and Redis message bus, YAML locales, the text formatter, server list ping, heartbeats, server selection, punishments, reports and the audit log. |
| `:core-paper` | Core plugin for Paper backends. Chat format with rank prefixes, tablist, sidebar and menu frameworks, economy commands, network overview. |
| `:core-velocity` | Core plugin for the proxy. MOTD, maintenance mode, `/hub`, `/sendall`, lobby fallback when a backend drops a player. |
| `:lobby-paper` | Optional lobby gameplay on top of the core. |

## Using the API

Add `depend: [PrismarinCore]` to your `plugin.yml`, then:

```java
Prismarin prismarin = PrismarinProvider.get();

Currency coins = prismarin.economy().primaryCurrency();
prismarin.economy().deposit(player.getUniqueId(), coins, 50);

prismarin.events().subscribe(BalanceChangedEvent.class,
        event -> getLogger().info(event.uuid() + " now has " + event.newBalance()));

List<NetworkServer> bedwars = prismarin.servers().byGroup("bedwars");
```

On Paper the same instance is registered in the Bukkit services manager. On Velocity, depend on `prismarin-core`.

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

`LOCAL` and `SQLITE` make a single server usable with no infrastructure at all, the message bus then runs in process. `MONGO` or `MYSQL` plus `redis.enabled: true` turns the same jars into a network where profiles, messages and heartbeats are shared across every server.

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

261 unit tests across 43 test classes run on every build. 5 more classes are integration tests, tagged separately, that spin up real MongoDB, Redis, MySQL and MariaDB containers through Testcontainers.

```bash
./gradlew build             # compile plus unit tests
./gradlew integrationTest   # Testcontainers backed integration suite
./gradlew dist              # collect the plugin jars in dist/
```

## Status

Version 0.4.0 adds storage migration, silent containers in vanish and an optional bridge that gives backend operators PRISMARIN permissions on the proxy. Version 0.3.0 added game server adapters with automatic server selection, SQL storage and a cross server admin system on top of 0.2.0, which turned the former single network into a framework with configurable branding and currencies, a public API, switchable systems and modules, the network overview and bridges to Vault and PlaceholderAPI. Next on the roadmap is publishing: a Maven package, releases and documentation. See `ROADMAP.md`.

## License

MIT
