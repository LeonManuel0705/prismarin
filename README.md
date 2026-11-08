# PRISMARIN

A framework for Minecraft networks on Paper and Velocity. Profiles, currencies, localisation, events, a cross server message bus and a server directory live in one shared layer, and a network plugs its own game servers in behind it, whether that is an existing game plugin or a mode written from scratch.

The point of the architecture is that a game mode never talks to a database. It asks the profile service for a player, the economy service for a balance, or the message bus to reach another server. The implementation behind those interfaces can be JSON files on a laptop or MongoDB and Redis across a real network, without a single line changing in the game mode.

## What a network gets

| Area | What it does |
|:---|:---|
| Profiles | One profile per player across every server, with locale, level, settings and balances |
| Economy | Any number of currencies the network defines itself, with atomic transactions on every backend |
| Branding | Name, colours and chat prefix come from the config, every bundled text uses `%brand%` and `%brand_prefix%` |
| Text | MiniMessage, `&` colour codes, `&#RRGGBB` and `&x&R&R&G&G&B&B` hex, placeholders written as `%name%` |
| Servers | A directory with free groups and properties per server, used by the navigator and by plugins |
| Network overview | `/network` opens a live inventory with status, players, ping, version, TPS, memory and uptime per server |
| Events | `ProfileLoadedEvent`, `ProfileUnloadedEvent` and `BalanceChangedEvent` on proxy and backends |
| Vault | Registers as the Vault economy, so every shop or game plugin pays out network currency |
| PlaceholderAPI | `%prismarin_balance%`, `%prismarin_group_online_bedwars%` and more for other plugins, and PlaceholderAPI placeholders inside PRISMARIN's own tablist, sidebar and chat |
| Compatibility | Switches its own tablist, sidebar or chat format off when TAB, a scoreboard plugin or a chat plugin is installed |
| Systems and modules | Whole systems (economy, levels, network status) and single features (chat, tablist, sidebar, join messages, commands, proxy features, lobby parts) can each be switched off |
| Lobby | An optional ready made lobby with navigator, profile menu, player hider and double jump |

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
| `:api` | The contract for other plugins. `Prismarin`, profiles, economy, events, locale, messaging, server directory and status. |
| `:shared` | The implementations. Config loading, JSON and MongoDB storage, local and Redis message bus, YAML locales, the text formatter, server list ping and heartbeats. |
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

`storage.mode: LOCAL` keeps profiles in JSON files and runs the message bus in process, which makes a single server usable with no infrastructure at all. `MONGO` plus `redis.enabled: true` turns the same jars into a network where profiles, messages and heartbeats are shared across every server.

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

181 unit tests across 33 test classes run on every build. Two more classes are integration tests, tagged separately, that spin up real MongoDB and Redis containers through Testcontainers.

```bash
./gradlew build             # compile plus unit tests
./gradlew integrationTest   # Testcontainers backed integration suite
./gradlew dist              # collect the plugin jars in dist/
```

## Status

Version 0.2.0 turns the former single network into a framework: configurable branding and currencies, server groups, a public API with events, switchable systems and modules, the network overview and bridges to Vault and PlaceholderAPI. Next on the roadmap are adapters for foreign game plugins, MySQL storage and a cross server admin system. See `ROADMAP.md`.

## License

MIT
