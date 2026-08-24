# PRISMARIN

A Minecraft server network built as a single Gradle monorepo: one Velocity proxy, several
Paper backends, and a shared service layer underneath them that owns player profiles,
currency, localisation and cross server messaging.

The point of the architecture is that a game mode should never talk to a database. It asks
the profile service for a player, the economy service for a balance, or the message bus to
reach another server, and the implementation behind that interface can be a JSON file on a
laptop or MongoDB and Redis across a real network without a single line changing in the
game mode.

## Architecture

```
        api          pure contracts, zero platform dependencies
         |
       shared        implementations: storage, message bus, config, locale
        /   \
core-velocity  core-paper
    proxy         backends
                     |
                 lobby-paper
```

| Module | Purpose |
|:---|:---|
| `:api` | Java interfaces only. Profiles, economy, messaging, locale, server directory, branding. No Bukkit, no Velocity, no driver imports. |
| `:shared` | The implementations. Configurate based config loading, JSON file and MongoDB profile storage, a local and a Redis backed message bus, YAML locale service. |
| `:core-paper` | Core plugin for Paper backends. Chat formatting with rank prefixes, tablist, sidebar framework, menu framework, economy commands. |
| `:core-velocity` | Core plugin for the proxy. MOTD, maintenance mode, `/hub`, `/sendall`, lobby fallback when a backend drops a player. |
| `:lobby-paper` | Lobby gameplay. Spawn handling, void protection, navigator and profile menus, player hider, double jump, animated sidebar, build mode. |
| `build-logic` | Precompiled convention plugin. Java 21, JUnit 5, and the split between unit and integration test tasks. |

## Storage swapping

Every server writes a default `config.yml` on first start with `storage.mode` set to
`LOCAL`, which keeps profiles in JSON files and runs the message bus in process. That makes
a single server usable with no infrastructure at all. Switching `storage.mode` to `MONGO`
and enabling Redis turns the same jars into a network where profiles and messages are
shared across every backend. The game code does not know which mode it is in.

Economy operations are atomic on both backends. A balance change is a conditional update,
not a read followed by a write, so two servers deducting coins from the same player at the
same moment cannot both succeed against a stale value.

## Testing

127 tests across 20 test classes. Unit tests run on every build. Integration tests are
tagged separately and spin up real MongoDB and Redis containers through Testcontainers,
which means the storage layer is verified against the actual databases rather than against
a mock that agrees with whatever the code does.

```bash
./gradlew build             # compile plus unit tests
./gradlew integrationTest   # Testcontainers backed integration suite
```

CI runs both on every push.

## Running a network locally

1. `./gradlew build`
2. Put `core-velocity` on the Velocity proxy, and `core-paper` plus `lobby-paper` on each
   Paper backend
3. Give every server a unique `server-id` in its generated `config.yml`
4. For a multi server setup, set `storage.mode` to `MONGO` and enable Redis

Locale files for German and English are seeded on first start and use MiniMessage, so
every player facing string is translatable and none of them are hardcoded in the plugins.

## Status

Phase zero is complete and deployed: the foundation, the shared services and a fully
playable lobby. The roadmap in `ROADMAP.md` lays out the path from here through a game
engine abstraction, four game modes, progression, cosmetics, staff tooling and finally
horizontal scaling.

## License

MIT
