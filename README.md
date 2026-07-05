# PRISMARIN

Monorepo for the PRISMARIN Minecraft network: a Velocity proxy with Paper backends, shared services for profiles, economy, cross-server messaging and localization.

## Modules

| Module | Purpose |
| --- | --- |
| `:api` | Pure Java contracts (profiles, economy, messaging, locale, servers, brand). No platform dependencies. |
| `:shared` | Implementations: Configurate config loading, JSON-file and MongoDB profile storage, local and Redis message bus, YAML locale service. |
| `:core-paper` | Core plugin for Paper backends. |
| `:core-velocity` | Core plugin for the Velocity proxy. |
| `:lobby-paper` | Lobby gameplay plugin for Paper. |
| `build-logic` | Precompiled convention plugin `prismarin.java` (Java 21, JUnit 5, integration-test split). |

## Build

```
./gradlew build
```

Runs compilation and all unit tests. Integration tests (tagged `integration`, backed by Testcontainers with MongoDB and Redis) are excluded from the default test task and run in CI only:

```
./gradlew integrationTest
```

## Local network

1. Build the plugin jars with `./gradlew build`.
2. Drop `core-velocity` on the Velocity proxy and `core-paper` plus `lobby-paper` on each Paper backend.
3. On first start every server writes a default `config.yml` (storage `LOCAL`, Redis disabled) and seeds `messages_de.yml` / `messages_en.yml`; set a unique `server-id` per server.
4. For a multi-server setup switch `storage.mode` to `MONGO` and enable Redis so profiles and messages are shared across the network.
