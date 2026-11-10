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

## Strafen

`PunishmentFlow` bündelt alles: Ziel auflösen (echter Name, Nick über `NickDirectory`, Offline-Profil), Vorlage und Eskalation, Speichern über `PunishmentService`, Durchsetzen (Trennen, Mute an den Spielserver, Hinweis), Audit-Log, Team-Meldung mit Klick auf `/check` und die optionale öffentliche Meldung. Jede Strafe geht über `PunishmentService.punish`, das `PunishmentIssuedEvent` veröffentlicht; darüber schließt `ReportFlow` offene Reports. Alles läuft im Proxy, Redis ist nicht nötig.

| Befehl | Wirkung | Recht |
| --- | --- | --- |
| `/ban <spieler> <grund\|vorlage>` | dauerhaft; `/ban <spieler> 7d <grund>` bleibt als Tempban möglich | `prismarin.staff.ban` |
| `/tempban <spieler> <dauer> <grund>` | befristet | `prismarin.staff.tempban` oder `.ban` |
| `/ipban <spieler> [dauer] <grund>` | sperrt die Verbindung eines Online-Spielers, nur mit `ip-bans.enabled` | `prismarin.staff.ipban` |
| `/mute`, `/tempmute` | wie `/ban` und `/tempban` | `prismarin.staff.mute`, `prismarin.staff.tempmute` |
| `/kick <spieler> [grund\|vorlage]` | nur online und sichtbar | `prismarin.staff.kick` |
| `/warn <spieler> <grund\|vorlage>` | Verwarnung | `prismarin.staff.warn` |
| `/punish <spieler> [vorlage] [notiz]` | ohne Vorlage Menü auf dem Spielserver, sonst nächste Stufe | `prismarin.staff.punish` |
| `/unban`, `/unmute <spieler> [grund]` | hebt auf, der Grund landet in der Akte | `prismarin.staff.unban` oder `.ban`, `prismarin.staff.unmute` oder `.mute` |
| `/history <spieler> [seite]` | Akte mit Status (aktiv, abgelaufen, aufgehoben, ersetzt) | `prismarin.staff.history` |
| `/check <spieler>` | aktive Strafen, Zähler, Verstöße pro Vorlage mit nächster Stufe, offene Reports | `prismarin.staff.check` oder `.history` |

Ein Grund, der mit einer Vorlagen-ID beginnt, wendet die Vorlage an; der Rest wird zur Notiz. Wer nur `prismarin.staff.punish` hat, kann ausschließlich Vorlagen nutzen. Weitere Rechte: `prismarin.punish.exempt` (von Spielern nicht bestrafbar), `prismarin.punish.ipban.bypass` (Login trotz IP-Ban der eigenen Adresse), pro Vorlage optional ein eigenes Recht. Eine kürzere Strafe ersetzt keine längere derselben Art, dafür zuerst aufheben.

`punishments.yml` (Kommentare über `YamlComments`):

| Key | Default | Bedeutung |
| --- | --- | --- |
| `appeal-url` | leer | `%appeal%`, `%appeal_line%` erscheint nur, wenn gesetzt |
| `time-zone` | leer (Zeitzone des Rechners) | für `%expires%` und `%date%` |
| `show-actor-to-player` | `false` | `%actor%` in Bildschirmen zeigt sonst „Team“, ebenso bei genickten oder unsichtbaren Teammitgliedern |
| `ip-bans.enabled` | `true` | registriert `/ipban` und prüft Adressen beim Login; Adressen nur als gesalzener SHA-256 (IPv6 als /64) |
| `escalation.count-lifted` | `false` | aufgehobene Strafen zählen nicht als Verstoß |
| `public-broadcast.enabled` | `false` | Meldung an alle ohne `prismarin.staff`, mit Nick, nie bei Unsichtbaren |
| `public-broadcast.kinds` | `[ban, tempban, ipban]` | `ban`, `tempban`, `ipban`, `mute`, `tempmute`, `kick`, `warn` |
| `templates.<id>` | hacking, chat, spam, name, teaming, bugusing, evasion | `name.<sprache>`, `icon`, `type`, `steps`, `permission`, `forget-after` |

Schritte: eine Dauer (`30m`, `12h`, `7d`, `1w`, `1y`) oder `permanent` im Typ der Vorlage, oder mit eigenem Typ (`mute 2h`, `ban 7d`, `ban permanent`, `warn`, `kick`). Der letzte Schritt wiederholt sich. Gezählt werden frühere Strafen derselben Vorlage, ersetzte zählen mit, Einträge älter als `forget-after` nicht.

Texte unter `punishment` in `messages_de.yml`/`messages_en.yml`: `screen.ban|tempban|ipban|kick`, `notice.mute|tempmute|warn|unmuted`, `staff.*`, `public.*`, `check.*`, `history.*`, `menu.*`, `usage.*`, Einheiten unter `time.*`, Datumsformat `date-format`. Platzhalter: `%player%`, `%type%`, `%reason%`, `%duration%`, `%remaining%`, `%expires%`, `%date%`, `%id%`, `%actor%`, `%appeal%`, `%appeal_line%`, `%template%`. Mehrzeilige Bildschirme mit `<newline>`.

Kanal `prismarin:moderation`, neu: Proxy an Spielserver `punish-menu` (`uuid`, `target`, `target-name`, `title`, `head`, `head-lore`, `templates[]` mit `id`, `icon`, `name`, `lore`, alles fertig gerenderte MiniMessage-Texte), Spielserver an Proxy `punish` (`uuid`, `target`, `template`); der Proxy nimmt es nur von einer `ServerConnection` an, wenn der Träger der Absender ist und das Menü für genau dieses Ziel und diese Vorlage in den letzten zwei Minuten geöffnet wurde. `mute` trägt zusätzlich `id` und `appeal`.

Gespeicherte Strafen bleiben lesbar: `Punishment` hat die neuen Felder `template`, `note`, `addressHash`, `revokeReason`, `replacedBy`, alte Dokumente ohne sie werden mit leeren Werten gelesen.
