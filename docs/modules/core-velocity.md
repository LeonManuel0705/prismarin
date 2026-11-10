# Modul `:core-velocity` — PrismarinCore (Velocity)

## Anforderungen

- Proxy-seitiger Kern: gebrandetes MOTD, Wartungsmodus mit Bypass, `/hub`-Navigation, `/sendall`, Lobby-Fallback bei Server-Kicks.
- Nutzt dieselben `:shared`-Bausteine wie Paper (ConfigLoader, YamlLocaleService, MessageBusFactory), aber ohne Profil-Storage — der Proxy hält keine Spielerdaten.
- Konfigurationsänderungen zur Laufzeit (`/maintenance`) werden asynchron persistiert, Lesen ist immer lock-frei über ein `volatile`-Snapshot-Record.

## Architektur

Ein `@Plugin(id = "prismarin-core")` mit Velocity-Annotation-Processing; kein Guice-Modul, die Objektgraph-Verdrahtung passiert von Hand in `onProxyInitialize`. Das Jar ist ein Shadow-Jar (`prismarin-velocity.jar`): `:api`, `:shared` und alle Runtime-Bibliotheken werden nach `net.prismarin.libs.*` relociert (configurate, geantyref, kyori-option, snakeyaml, lettuce, netty, reactor, reactivestreams, mongodb, bson, HikariCP, MariaDB-Treiber); `org.sqlite` bleibt unrelociert, weil die JNI-Namen der nativen Bibliothek daran hängen. Service-Dateien werden mit `mergeServiceFiles()` zusammengeführt. Seit 0.3.0 liegt das Admin-System hier (`velocity.admin`): Team-Chat, Strafen, Reports, Wartung pro Server, Alerts, Vanish-Abgleich über den Kanal `prismarin:moderation`. Executor ist ein Virtual-Thread-Pool, MessageBus kommt aus `MessageBusFactory` (Redis oder lokal je `config.yml`).

## Klassenstruktur

```
net.prismarin.velocity
├── PrismarinVelocityPlugin   Lifecycle: Config/Locale-Seeding, Listener- und Command-Registrierung,
│                             MessageBus-Aufbau, Shutdown-Cleanup
├── command
│   ├── HubCommand            /hub, /lobby — verbindet zur konfigurierten Lobby
│   ├── MaintenanceCommand    /maintenance on|off|status mit Tab-Vervollständigung
│   └── SendAllCommand        /sendall <server> — schickt alle Spieler auf einen Server
├── config
│   ├── ProxyConfig           immutables Record + withMaintenance(...)
│   ├── ProxyConfigCodec      Defaults, load/save von proxy.yml
│   └── ProxyConfigStore      volatile Snapshot + asynchrones Persistieren
├── listener
│   ├── LobbyRedirectListener KickedFromServerEvent -> Redirect in die Lobby mit Grund
│   ├── MaintenanceLoginListener  LoginEvent-Deny ohne Bypass-Permission
│   └── PingListener          ProxyPingEvent: MOTD, max-players, Wartungs-Version (Protokoll -1)
├── locale
│   └── ProxyMessages         Render/Send über LocaleService, Locale aus getEffectiveLocale
└── motd
    └── MotdBuilder           zweizeilige MiniMessage-Description, Wartungsvariante
```

## Events

| Listener | Events |
| --- | --- |
| `PrismarinVelocityPlugin` | `ProxyInitializeEvent`, `ProxyShutdownEvent` |
| `PingListener` | `ProxyPingEvent` — MOTD aus `ProxyConfig`, im Wartungsmodus Version `Wartung` mit Protokoll `-1` |
| `MaintenanceLoginListener` | `LoginEvent` — Deny mit `maintenance.kick`, außer `prismarin.maintenance.bypass` |
| `LobbyRedirectListener` | `KickedFromServerEvent` — Redirect zur Lobby mit `kick.redirected(-reason)`, außer der Kick kam von der Lobby selbst oder während eines Connects |

## Commands

| Command | Beschreibung | Permission |
| --- | --- | --- |
| `/hub` (Alias `/lobby`) | Verbindet den Spieler mit dem `lobby-server` | keine |
| `/sendall <server>` | Schickt alle Spieler (außer bereits dort) auf den Zielserver | `prismarin.admin.sendall` |
| `/maintenance on\|off\|status` | Wartungsmodus schalten/abfragen, persistiert nach `proxy.yml` | `prismarin.admin.maintenance` |

## Permissions

| Permission | Wirkung |
| --- | --- |
| `prismarin.admin.maintenance` | `/maintenance` |
| `prismarin.admin.sendall` | `/sendall` |
| `prismarin.maintenance.bypass` | Login trotz aktiven Wartungsmodus |

Velocity kennt keine `plugin.yml`-Permission-Defaults; Vergabe über LuckPerms (Velocity-Modul) oder Proxy-Konfiguration.

## Konfiguration

- `config.yml` (Seed aus dem Jar): dieselben `CoreConfig`-Keys wie Paper, Default `server-id: proxy`, `storage.mode: LOCAL`, Redis aus. Der Storage-Teil wird auf dem Proxy nicht genutzt, nur `server-id`, `redis.*` und `locale.default`.
- `proxy.yml` (`ProxyConfigCodec`):

| Key | Default | Bedeutung |
| --- | --- | --- |
| `motd.line1` | Brand-Gradient `PRISMARIN` | MOTD Zeile 1 (MiniMessage) |
| `motd.line2` | `Lobby · Creative · Minigame — 1.8 bis 1.21` | MOTD Zeile 2 |
| `maintenance.enabled` | `false` | Wartungsmodus |
| `maintenance.motd.line1/line2` | Brand + Wartungstext | MOTD im Wartungsmodus |
| `maintenance.version-name` | `Wartung` | Angezeigter Versionsname (Protokoll -1) |
| `max-players` | `200` | Angezeigtes Spielerlimit im Ping |
| `lobby-server` | `lobby` | Ziel für `/hub` und Kick-Redirects |

- `locale/messages_de.yml` / `locale/messages_en.yml`: 24 Keys, identische Struktur; werden beim Start ins Datenverzeichnis geseedet.

## Tests

- `ProxyConfigCodecTest` (3): Defaults, Roundtrip load/save, Teil-Konfiguration.
- `ProxyConfigStoreTest` (2): Snapshot-Semantik, Maintenance-Toggle mit Persistenz.
- `ProxyMessagesTest` (4): Locale-Auflösung (effective locale, Fallback, Konsole), Rendering.
- `MotdBuilderTest` (3): Normal-/Wartungs-MOTD, Zeilenaufbau.

## Erweiterungspunkte

- Neue Proxy-Commands/-Listener werden in `onProxyInitialize` registriert und bekommen `ProxyConfigStore`/`ProxyMessages` per Konstruktor.
- `ProxyConfig` wächst als Record plus `ProxyConfigCodec`-Key — `withX`-Kopiermethoden für weitere Laufzeit-Schalter ergänzen.
- Der bereits verdrahtete `MessageBus` ist der Ankerpunkt für Phase 1 (Cross-Server-Chat, Party-Moves, JoinMe): Proxy publiziert/abonniert dieselben Channels wie die Paper-Server.
