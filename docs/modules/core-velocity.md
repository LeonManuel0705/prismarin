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

## Drivers loaded on demand

The proxy jar does not contain the SQLite and MongoDB drivers or the Redis client, they would take it from 3 to 24 MB. On start the proxy reads `prismarin-libraries.txt` from its jar, a list of every driver file with its SHA-256 checksum that the build writes from the versions in `gradle/libs.versions.toml`. Only the files for the configured `storage.mode` are fetched, plus the Redis client when `redis.enabled` is on. `/prismarinproxy migrate` fetches the files for both modes. `LOCAL`, `MYSQL` and `MARIADB` without Redis fetch nothing.

| Mode | Files |
|:---|:---|
| `SQLITE` | `org.xerial:sqlite-jdbc` |
| `MONGO` | `org.mongodb:mongodb-driver-sync`, `mongodb-driver-core`, `bson`, `bson-record-codec` |
| `REDIS` | `io.lettuce:lettuce-core`, seven `io.netty` modules, `reactor-core`, `reactive-streams` |

Velocity ships its own Netty, so the Redis files are relocated once with `jar-relocator` (`io.netty` to `net.prismarin.libs.netty`, likewise `io.lettuce`, `reactor` and `org.reactivestreams`) into `libraries/relocated/` before they are loaded. The rules are built at runtime from parts, otherwise the shadow plugin would rewrite them while building.

Files land in `plugins/prismarin-core/libraries/`. A file whose checksum does not match is never loaded. Files that are already there with the right checksum are used without any download, so a proxy without internet works after copying the jars into that folder by hand. The source is Maven Central, `-Dprismarin.libraryRepository=<url>` or the environment variable `PRISMARIN_LIBRARY_REPOSITORY` point to a mirror. Loading goes through `PluginManager#addToClasspath`, so the drivers stay inside PRISMARIN's class loader. Paper servers do the same through `libraries:` in `plugin.yml`.

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
| `templates.<id>` | hacking, chat, spam, name, teaming, bugusing, evasion | `name.<sprache>`, `description.<sprache>` (optional, kurze Notiz im Menü), `icon`, `type`, `steps`, `permission`, `forget-after`; Reihenfolge im File = Reihenfolge im Menü, `templates: {}` = keine Vorlage, fehlender Abschnitt = Defaults |

Schritte: eine Dauer (`30m`, `12h`, `7d`, `1w`, `1y`) oder `permanent` im Typ der Vorlage, oder mit eigenem Typ (`mute 2h`, `ban 7d`, `ban permanent`, `warn`, `kick`). Der letzte Schritt wiederholt sich. Gezählt werden frühere Strafen derselben Vorlage, ersetzte zählen mit, Einträge älter als `forget-after` nicht.

Texte unter `punishment` in `messages_de.yml`/`messages_en.yml`: `screen.ban|tempban|ipban|kick`, `notice.mute|tempmute|warn|unmuted`, `staff.*`, `public.*`, `check.*`, `history.*`, `menu.*`, `usage.*`, Einheiten unter `time.*`, Datumsformat `date-format`. Platzhalter: `%player%`, `%type%`, `%reason%`, `%duration%`, `%remaining%`, `%expires%`, `%date%`, `%id%`, `%actor%`, `%appeal%`, `%appeal_line%`, `%template%`. Mehrzeilige Bildschirme mit `<newline>`.

Kanal `prismarin:moderation`, neu: Proxy an Spielserver `punish-menu` (`uuid`, `target`, `target-name`, `title`, `head`, `head-lore`, `templates[]` mit `id`, `icon`, `name`, `lore`, alles fertig gerenderte MiniMessage-Texte), Spielserver an Proxy `punish` (`uuid`, `target`, `template`); der Proxy nimmt es nur von einer `ServerConnection` an, wenn der Träger der Absender ist und das Menü für genau dieses Ziel und diese Vorlage in den letzten zwei Minuten geöffnet wurde. `mute` trägt zusätzlich `id` und `appeal`.

Gespeicherte Strafen bleiben lesbar: `Punishment` hat die neuen Felder `template`, `note`, `addressHash`, `revokeReason`, `replacedBy`, alte Dokumente ohne sie werden mit leeren Werten gelesen.

## Friends and parties: roles, settings and API

### Party roles

| Role | May |
| --- | --- |
| Leader | everything: invite, kick moderators and members, promote, demote, transfer the lead, change settings, warp, disband |
| Moderator | invite (unless `invite` is `leader`), kick members (not moderators or the leader), take back invites, change settings |
| Member | chat (unless `chat` says otherwise), look at members, roles and settings, leave; invite only when `invite` is `members` |

The proxy checks every action again (`PartyManager` with `PartyRules` from `:api`), menus on the lobby only show what the proxy allows. When the leader leaves, the first moderator takes over, otherwise the oldest member. When the leader disconnects, the lead moves to an online moderator first and the old leader stays as moderator. `/party transfer` also keeps the old leader as moderator.

Commands: `/party promote <player>` (member to moderator), `/party demote <player>`, `/party transfer <player>`, `/party join <player>` (accepts an invite or joins an open party), `/party settings [<setting> <value>]`. `/party list` and the party chat mark the leader with ♛ and moderators with ✦.

| Setting | Values | Default in `social.yml` |
| --- | --- | --- |
| `invite` | `leader`, `moderators`, `members` | `party.defaults.invite: moderators` (filled from the old `members-can-invite` once) |
| `access` | `invite-only`, `friends` (friends of the leader), `open` | `party.defaults.access: invite-only` |
| `follow` | `on`, `off`, only offered while `party.follow.enabled` is true | `party.defaults.follow: on` |
| `chat` | `members`, `moderators`, `leader` | `party.defaults.chat: members` |

`/party settings` without arguments shows the party settings with change buttons for the leader and moderators, then the personal settings. Keys that are no party setting (`requests`, `party-invites`, ...) still change personal settings as before.

### API for other proxy plugins

`PrismarinProvider.get().friends()` and `.parties()` return `Optional<FriendService>` and `Optional<PartyService>` from `net.prismarin.api.social`; they are empty when the module is off in `config.yml`. Read models: `Party` (id, leader, members, invited, settings), `PartyMember` (uuid, real name, `PartyRole`), `PartySettings`, `FriendInfo`. `Party.can(actor, PartyAction, target)` answers role questions with the same rules the proxy uses.

```java
Prismarin prismarin = PrismarinProvider.get();
prismarin.parties().ifPresent(parties -> {
    parties.partyOf(player.getUniqueId()).ifPresent(party -> {
        for (PartyMember member : party.members()) {
            logger.info("{} is {}", member.name(), member.role().id());
        }
    });
    parties.invite(leader, target).thenAccept(result -> {
        if (!result.success()) {
            logger.info("Invite failed: {}", result.id());
        }
    });
});
prismarin.friends().ifPresent(friends -> friends.areFriends(first, second)
        .thenAccept(together -> logger.info("Friends: {}", together)));
prismarin.events().subscribe(PartyJoinedEvent.class, event ->
        logger.info("{} joined the party of {}", event.player(), event.party().leader()));
```

Actions (`invite`, `join`, `leave`, `kick`, `promote`, `demote`, `transfer`, `disband`, `warp`, `changeSettings`, `sendRequest`, `remove`) run as the acting player with the same checks and chat feedback as the commands and return a `SocialResult`. The acting player has to be online on the proxy (`UNAVAILABLE` otherwise); `FriendService.remove` also works offline. `sendRequest` respects vanish and nicks like `/friend add`. Snapshots carry real names, nicks are a display matter.

Events on the `EventBus`, fired on a worker thread after the change: `FriendRequestEvent`, `FriendAddedEvent`, `FriendRemovedEvent`, `PartyCreatedEvent`, `PartyInviteEvent`, `PartyJoinedEvent`, `PartyLeftEvent` (reason `LEFT`, `KICKED`, `DISCONNECTED`), `PartyRoleChangedEvent` (also for transfers and hand overs), `PartySettingsChangedEvent`, `PartyDisbandedEvent`. A disband fires only `PartyDisbandedEvent`, a party that ends because the last other player left fires `PartyLeftEvent` first.

### Channel `prismarin:moderation`

| Type | Direction | Content |
| --- | --- | --- |
| `party-sync` | proxy to every server with a member or invitee of the changed party, and to a server a party member just joined | `cause` (`change` or `sync`), `before`, `after` (party JSON with `id`, `leader`, `members[]` with `uuid`, `name`, `role`, `invited[]`, `settings`), `dissolved`, `reason`, `actor` |
| `social` | server to proxy | new actions `party-join`, `party-demote`, `party-transfer`, `party-setting` (with `options`: `invite`, `access`, `follow`, `chat`), `friend-request`; `party-promote` now makes a moderator |
| `social-view` | proxy to server | now with `result` (`SocialResult` id) and in `party` the fields `role`, `can-warp`, `can-disband`, `can-settings`, `can-revoke`, `settings[]`, per member `role`, `can-kick`, `can-promote`, `can-demote`, `can-transfer` |
| `social-query` | server to proxy | `id`, `query` (`party`, `friends`, `are-friends`), `player`, `other` |
| `social-answer` | proxy to server | `id`, `enabled`, `party` or `friends[]` (at most 250, plus `total`) or `friends` as boolean |

The proxy only accepts these from a `ServerConnection` whose player is the `uuid` of the message.

## Players for proxy plugins

`PrismarinProvider.get().players()` returns the same `PlayerService` as on Paper (see the player section in `core-paper.md` for the read model, the viewer rules and the attribute registry), filled from what the proxy knows:

| Field | Source on the proxy |
| --- | --- |
| `realName` | the Velocity username |
| `nick` | reported by the backend the player is on (`player-state`), so it is exactly what that server shows. Until the first report after a server switch the stored nick claim (collection `nick-claims`, read in the background every five seconds) is used |
| `locale` | the language the backend reported (`ProxyMessages.localeOf`), otherwise the client language |
| `server`, `world` | current server, world as reported by the backend |
| `vanished` | `VanishRegistry` |
| `level` | profile, loaded on login and after every server switch |
| `rank`, `realRank` | not rendered on the proxy, always `RankDisplay.NONE` |

`online`, `onlinePlayers`, `displayName`, `resolveOnline` and `suggestions` cover every player on the network and never block. `find` and `findByRealName` add offline players from the profile storage, `resolve` additionally resolves real names of offline players. The proxy answers `player-query` messages from backends with the same data.

```java
PlayerService players = PrismarinProvider.get().players();
players.resolve(sender.getUniqueId(), args[0]).thenAccept(found -> found.filter(NetworkPlayer::online)
        .ifPresentOrElse(target -> sender.sendMessage(Component.text(
                        players.displayName(sender.getUniqueId(), target) + " is on " + target.server())),
                () -> sender.sendMessage(Component.text("Not online"))));

prismarin.events().subscribe(ServerChangedEvent.class, event ->
        logger.info("{} went from {} to {}", event.player(), event.previousServer(), event.currentServer()));
prismarin.events().subscribe(LocaleChangedEvent.class, event -> cache.invalidate(event.player()));

players.attributes().register(PlayerAttribute.of("queue", player -> queues.position(player.uuid())));
```

Events on the proxy: `ServerChangedEvent` after every `ServerPostConnectEvent` (`previousServer` is empty on login), `LocaleChangedEvent` when a backend reports a different language. Attributes registered on the proxy are visible to other proxy plugins; they are not sent to backends.

Classes: `ProxyPlayerService` (service, channel handler, listeners), `ProxyNickClaims`, `ProxyPlayers` (registration). `PlayerLocaleRegistry.onChange` notifies about language changes. Tests: `ProxyPlayerRulesTest`, `PlayerLocaleRegistryTest`.

## Levels for proxy plugins

`PrismarinProvider.get().levels()` returns the same `LevelService` as on Paper while `systems.levels` is on (see `api.md`), so a proxy plugin can reward XP too: `prismarin.levels().ifPresent(levels -> levels.addXp(uuid, 100, "daily-login"))`. `XpGainedEvent` and `LevelChangedEvent` fire on the proxy's event bus for changes made on the proxy. `vanish()` and `staffMode()` are empty on the proxy, both live on the backends.

## Reports, ban lists and the moderation API

### `/reports` as a staff menu

On a PRISMARIN backend `/reports` opens a six row menu, everywhere else (console, other backends) it prints the chat list. Every open case is a head of the reported player: amount = number of reporters, glowing when urgent (`reports.priority-reporters`), lore with coloured reasons, reporters, server, age, attached chat and the claim state. The bottom row pages (28 cases per page), switches the order (`urgent first`, `newest first`, `oldest first`) and the view (`all`, `free`, `mine`, `with chat`), the book on top shows the counts and refreshes. A click opens the case view: target head (nick, online state, record, reports in total, client), every reporter with reason and age, claim state, chat preview, the action buttons (go there, record, punish, configured and plugin actions), Done, Unfounded, back.

Opening a case claims it for that staff member (`ReportClaimedEvent`), every action on it refreshes the claim. After `reports.claim-minutes` (default `10`, `0` never) without any action the case is free again; a task releases idle claims every minute (`ReportReleasedEvent`). A case held by someone else shows a take over button. The chat list and new reports show the same state (`reports.status.*`).

| Command | Effect | Permission |
| --- | --- | --- |
| `/reports` | menu on PRISMARIN backends, chat list elsewhere | `prismarin.staff.reports` |
| `/reports list [page]` | chat list, 10 per page, clickable buttons per case | `prismarin.staff.reports` |
| `/reports claim <id>` | takes the case, also from someone else | `prismarin.staff.reports` |
| `/reports chat <id>` | prints the attached chat and refreshes your claim | `prismarin.staff.reports` |
| `/reports close <id> [invalid]` | done or unfounded | `prismarin.staff.reports` |
| `/reports action <id> <action>` | runs a configured or plugin action | `prismarin.staff.reports` plus the action permission |
| `/banlist [page]`, `/mutelist [page]` | active bans or mutes from the PRISMARIN store, newest first, 8 per page, with player, type, remaining time, reason, staff, case id; click runs `/check`, the button puts `/unban` or `/unmute` into the chat | `prismarin.staff.banlist` or `.ban` or `.history` or `prismarin.banlist` (mute list the same with `mute`) |

Viewers without `prismarin.staff` see the nick of a nicked player, never the real name, and `%actor%` follows `show-actor-to-player`; they get no `/check` click and no unban button. The proxy registers `/banlist`, so the vanilla list of the backend is no longer reached through the proxy.

### `admin.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `reports.reasons.<id>.color` | hacking `red`, chat `gold`, spam `yellow`, name `aqua`, teaming `light_purple`, bugusing `orange`, other `gray` | colour of the reason in the report menu, chat and `/reports`; a name, `#RRGGBB`, `&c` or `<red>`; missing colours of the bundled ids are added to existing files |
| `reports.reasons.<id>.name.<language>` | as before | now accepts MiniMessage and `&` codes; reports store the plain text, staff see the reason in their own language |
| `reports.claim-minutes` | `10` | minutes without action until a claimed case is free again, `0` keeps it until it is closed |
| `reports.menu-actions.<id>` | `{}` | extra buttons: `icon`, `name.<language>`, `command` (`%player%`, `%uuid%`, `%id%`, `%server%`, `%staff%`), `permission`, `console` (run as proxy console), `close` (close the menu, default `true`) |

Proxy commands of an action run directly, other commands are sent to the server of the staff member as chat input.

Texts in `messages_*.yml`: `reports.menu.*`, `reports.status.*`, `reports.action.custom*`, `reports.still-yours`, `reports.unknown-action`, `reports.list-previous|next`, `punishment.list.*`, `punishment.usage.banlist|mutelist`, `punishment.menu.description`. The punish menu lore is now next level, previous offences, the optional template description and the click hint, wrapped at 34 characters by `LoreWrap`; the full ladder stays in the hover of the chat fallback.

### Channel `prismarin:moderation`

| Type | Direction | Content |
| --- | --- | --- |
| `reports-menu` | proxy to server | `uuid`, `session`, `title`, `rows`, `items[]` with `slot`, `icon`, `name`, `lore[]` (rendered MiniMessage), optional `skull` (UUID), `amount`, `glow`, `action`, `close` |
| `reports-click` | server to proxy | `uuid`, `session`, `action` |
| `report-menu` | proxy to server | reason `name` is now a coloured MiniMessage text |

The proxy only takes `reports-click` from a `ServerConnection` whose player is the `uuid`, only for the latest menu it sent to that player (15 minutes), only with an action that menu contained, and checks `prismarin.staff.reports` and the action permissions again.

### API for other proxy plugins

`PrismarinProvider.get().reports()` returns `Optional<ReportService>`, `.punishmentTemplates()` returns `Optional<PunishmentTemplates>` (package `net.prismarin.api.moderation`); both are empty when the module is off. `PunishmentService.inForce(type)` lists every punishment of a type that is in force, newest first.

| Type | Purpose |
| --- | --- |
| `ReportService` | `reasons()`, `registerReason`, `unregisterReason`, `actions()`, `registerAction`, `unregisterAction`, `claimTimeout()`, `open()`, `openAgainst(uuid)`, `find(id)`, `file(reporter, reporterName, target, reason)`, `claim(id, staff)`, `release(id)`, `close(id, staff, verdict)` |
| `Report` | snapshot: id, target, real and shown name, server, client context, `reporters()` (`Reporter` with uuid, name, reason, time), chat, created, `holder` (empty when free), claimed at, open, closed by and at, `verdict` (null while open, `closedAs()`) |
| `ReportReason` | id, icon, names per language, colour (`#RRGGBB` or empty) |
| `ReportAction` | id, icon, names, permission, `closeMenu`, `Handler.run(staff, staffName, report)`; console runs use `ReportAction.CONSOLE` |
| `ReportVerdict` | `VALID`, `INVALID`, `PUNISHED` |
| `ReportSubmission`, `ReportOutcome` | result of `file`: `FILED`, `MERGED`, `DUPLICATE` |
| `PunishmentTemplates` | `templates()`, `template(id)`, `register`, `unregister`, `preview(target, id)`, `punish(target, id, actor[, note])` |
| `PunishmentTemplate`, `PunishmentTemplate.Step`, `PunishmentPreview` | template read model and the next step for a player (previous offences, level, levels, step) |

Events on the `EventBus`: `ReportFiledEvent(report)`, `ReportMergedEvent(report, reporter)`, `ReportClaimedEvent(report, staff, previous)` (new claim or take over, not on refreshes), `ReportReleasedEvent(report, staff)` (idle or `release`), `ReportClosedEvent(report, staff, verdict)` (also `PUNISHED` when a punishment closes the cases). Reasons, actions and templates from plugins come after the configured ones and cannot replace an id from `admin.yml` or `punishments.yml` (`IllegalArgumentException`); registering an id again replaces the plugin entry. `file` takes a reason id (plus details after a space) or free text and fails for players who never joined; chat is attached when the reason id is listed in `reports.chat-evidence.reasons` (or that list is empty). Action handlers and event listeners usually run on a worker thread: do not block in them. `punish` applies the next step like `/punish` with the actor name, fails for unknown templates, unknown players or when a longer punishment of that kind is active.

```java
Prismarin prismarin = PrismarinProvider.get();
prismarin.reports().ifPresent(reports -> {
    reports.registerReason(new ReportReason("xray", "DIAMOND_ORE",
            Map.of("de", "X-Ray", "en", "X-ray"), "#55FFFF"));
    reports.registerAction(new ReportAction("freeze", "ICE", Map.of("de", "Einfrieren", "en", "Freeze"),
            "myplugin.freeze", true, (staff, staffName, report) -> freezer.freeze(report.target())));
    reports.file(anticheatId, "AntiCheat", suspect, "hacking Reach 4.2").thenAccept(result ->
            logger.info("Report {} is {}", result.report().id(), result.outcome()));
});
prismarin.events().subscribe(ReportClosedEvent.class, event ->
        logger.info("{} closed #{} as {}", event.staff(), event.report().id(), event.verdict().id()));
prismarin.punishmentTemplates().ifPresent(templates -> templates.preview(suspect, "hacking")
        .thenCompose(next -> next.previousOffences() > 2
                ? templates.punish(suspect, "hacking", "AntiCheat", "automatic")
                : CompletableFuture.completedFuture(null)));
```


## Audit log, maintenance and staff alerts for plugins

`ProxyRemote.register` (called at the end of `onProxyInitialize`) registers `AuditService` (`LocalAuditService` on the proxy storage), `MaintenanceService` (`ProxyMaintenance`, only when `maintenance` or `server-maintenance` is on), `NotificationService` (`ProxyNotifications`) and `DataStorage` behind `Prismarin.audit()`, `.maintenance()`, `.notifications()` and `.storage()`. It also answers the backends: `network-request` messages on `prismarin:moderation` from a `ServerConnection` whose player is the `uuid`, and, with Redis, requests on the bus channel `prismarin:network`. `RemoteDispatcher` (`:shared`) turns each request into a call on the same API services, so a backend gets exactly what a proxy plugin gets. Every `ReportFiledEvent` is relayed as `network-event` to every server with players.

- `ProxyMaintenance` is the only place that switches maintenance. `/maintenance` uses it too, so every switch, from a command or a plugin, writes an audit entry (`maintenance`, target `network`, `server:<name>` or `group:<name>`, `on` or `off`) and fires `MaintenanceChangedEvent`. `setNetwork` fails when `modules.maintenance` is off, `setServer` and `setGroup` when `modules.server-maintenance` is off.
- `ProxyNotifications.notifyStaff` shows `alerts.plugin` (`%source%`, `%server%`, `%message%`) to every player with the alert permission and the console, rendered in the receiver's language, and fires `StaffAlertEvent`. `staffChat` uses `staff.chat` and `prismarin.staff.chat`, `broadcast` uses `alert.message` with the scopes of `/alert` and writes an `alert` audit entry with the server as actor.
- `%server%` is the backend the request came from (`proxy` for proxy plugins).

Classes: `ProxyRemote`, `ProxyMaintenance`, `ProxyNotifications`; texts `alerts.plugin` in both language files. The wire format and its tests are described in `shared.md`.

## Reload, update check and metrics

`/prismarinproxy reload` (permission `prismarin.admin.reload`) reloads the language files plus every part another plugin registered through `prismarin.reloads()`, and lists what failed. Storage, Redis and module switches need a restart.

`update-check` in `config.yml` asks `api.github.com/repos/LeonManuel0705/prismarin/releases/latest` on start and every `interval-hours` (default 6). A newer release is logged once, fires `UpdateAvailableEvent` and, with `notify-staff`, shows staff with `prismarin.admin.update` a clickable note on join.

`metrics.enabled` sends anonymous bStats data (bstats.org): the standard charts plus `storage_mode`, `redis`, `default_language`. bStats is shaded and relocated to `net.prismarin.libs.bstats`, `plugins/bStats/config.yml` switches it off for every plugin.

## Chat filter on the proxy

Module `chat-filter` in `config.yml`. The proxy has its own `chat-filter.yml` in `plugins/prismarin-core` with the same keys as a backend (see [`core-paper.md`](core-paper.md#chat-filteryml)) plus `proxy-commands`.

`ProxyChatFilter` listens to `CommandExecuteEvent` (order `EARLY`). For a player without `prismarin.chatfilter.bypass` it looks for a pattern from `proxy-commands.channels`, filters the message part and then denies the command (blocked) or rewrites it (changed; `command(...)` for proxy commands, `forwardToServer(...)` for backend commands). Commands with signed arguments (`SignedState.SIGNED_WITH_ARGS`) are left alone, because changing or denying them disconnects the client; the private and party commands of PRISMARIN run on the proxy and are never signed.

| Key | Default | Meaning |
| --- | --- | --- |
| `proxy-commands.enabled` | `true` | false keeps the proxy from filtering commands; backend hits are still handled |
| `proxy-commands.channels.private` | `msg`, `tell`, `w`, `whisper`, `m` with `<player> <message>`, `r` and `reply` with `<message>` | the default names of `social.yml` |
| `proxy-commands.channels.party` | `pc`, `partychat`, `party chat`, `p chat` with `<message>` | |
| `proxy-commands.channels.<name>` | | any channel of your own, for example `clan: ["cc <message>", "clan chat <message>"]` |

Each entry is the command, optional sub commands and `<argument>`s, and ends with the message. Longer command paths are tried first, wrong entries are logged and skipped. Tested in `ChatCommandPatternsTest`.

`ChatViolations` handles every hit, from the proxy itself and from `chat-violation` messages of the backends:

- **Staff alert** `chat-filter.staff` (`%player%`, `%server%`, `%rules%` with the localised action, `%message%`) with a hover (`%channel%`, `%details%`, `%shown%`) and a click on `/check`, for `prismarin.staff.chatfilter` and the console; at most one alert per player every two seconds.
- **Audit** entry by "Chat filter" (`chat-filter.name`), action `chat-filter.block`, `.replace`, `.warn` or `.log`, target the player, details `rules (channel): message` (200 characters).
- **Escalation**: `ViolationCounter` (in `:shared`) counts hits per player and rule for the whole network, backends and proxy together, in memory. When a rule reaches `violations` within `within`: alert `chat-filter.escalated`, audit `chat-filter.escalation`, `PunishmentFlow.punish(target, template, "Chat filter", note)` with the note `chat-filter.escalation-note` (`automatic after 3 hits (words)`), so the escalation steps of the template apply as for staff; nothing is punished when `modules.punishments` is off, the template does not exist (warning in the log) or the player has `prismarin.punish.exempt`. With `report: true`, `ReportFlow.fileWithEvidence` files a report by "Chat filter" (`ReportAction.CONSOLE`) with the reason `report-reason`, the details `chat-filter.report-details` and the counted messages as chat evidence (`> 12:00:01 Steve: text [words, private]`, at most 20 lines); within the merge window it joins an open report against the same player. Reports by `ReportAction.CONSOLE` never thank anyone. Then the counter for that rule starts again and `ChatFilterEscalatedEvent(player, name, rule, violations, template, reported)` fires.

Texts under `chat-filter` in both language files: `name`, `blocked`, `changed`, `warned`, `reason.<rule>`, `action.<action>`, `staff`, `staff-hover`, `escalated`, `escalation-note`, `report-details`. Permissions: `prismarin.chatfilter.bypass` (and `.<rule>`), `prismarin.staff.chatfilter`. Velocity has no permission defaults, give them with LuckPerms.

`ProxyChatFilter` is the `ChatFilterService` behind `Prismarin.chatFilter()` on the proxy; `filter` with a channel of your own is the way for proxy plugins (for example a clan chat) to run their messages through the same rules and escalation.

### Channel `prismarin:moderation`

| Type | Direction | Content |
| --- | --- | --- |
| `chat-violation` | server to proxy | `uuid`, `name`, `channel`, `message`, `shown`, `blocked`, `entries[]` (see [`core-paper.md`](core-paper.md#what-goes-to-the-proxy)) |

## Alt accounts

Module `alts` in `config.yml`. `AltTracker` listens to `LoginEvent` (order `LATE`, after bans and maintenance) and stores the address of every login as salted SHA-256 hash through `AddressKeys`, the same instance and salt the IP bans use (IPv6 as its /64 network). Plain addresses are never written anywhere.

| `alts.yml` | Default | Meaning |
| --- | --- | --- |
| `max-addresses-per-player` | `10` | addresses kept per player, the oldest goes first |
| `shared-address-limit` | `10` | an address with more accounts than this is treated as shared (school, mobile network, a proxy in front of the network) and links nobody |
| `retention` | `90d` | how long an address is remembered after its last login; older entries are removed one minute after the start and once a day |
| `alert-staff` | `true` | alert when an account joins that shares an address with a banned account |
| `enforce-bans` | `false` | such accounts are turned away with the ban screen of the banned account, and a new ban disconnects online alts |

On a login that is still allowed, for a player without `prismarin.alts.bypass`: when an alt has a ban in force, `alts.join-alert` goes to `prismarin.staff.alts` and the console (click: `/alts <player>`), the audit log gets `alts.banned-alt` and `BannedAltJoinEvent(player, name, bannedAlts, false)` fires. Without `enforce-bans` the login does not wait for this check. With `enforce-bans` (and `modules.punishments` on) the login waits, is denied with `PunishmentFlow.screen` of the alt's ban, staff see `alts.join-denied`, the audit log `alts.denied` and the event has `denied = true`; `PunishmentIssuedEvent` of a ban disconnects online alts without the bypass (`alts.removed` for staff).

| Command | Effect | Permission |
| --- | --- | --- |
| `/alts <player>` | accounts that share an address with the player: online server or last seen, addresses in common, marks for bans and mutes, hover with first and last seen, click for `/check` | `prismarin.staff.alts` |

`/check` and `/pinfo` show a line `Alts:` with up to six names (banned red, muted gold, click for `/check`) while the module is on. Texts under `alts` plus `info.alts` and `punishment.check.alts`.

Storage (shared `DocumentStore`, every storage mode): `alt-accounts/<uuid>` with name, first and last login and the newest `max-addresses-per-player` hashes with first and last use; `alt-addresses/<hash>` with up to `shared-address-limit + 1` accounts (uuid, name, first and last use). A lookup reads the player's hashes, the accounts behind each fresh hash and the current name of each alt; bans and mutes come from `PunishmentService.active`. Classes: `AltTracker`, `AltSettings`, `AltCommands`; shared `AltStore`, `DocumentAltService`. Tests: `AltSettingsTest`, `ChatFilterMessagesTest`, `AltStoreTest`, `RemoteAltsTest`.

`DocumentAltService` is the `AltService` behind `Prismarin.alts()`; backends ask the proxy with the network call `alts-find`.
