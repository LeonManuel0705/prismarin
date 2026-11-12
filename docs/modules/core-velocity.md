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
| `prismarin.clan.color` | `/clan color` (zusätzlich zur Rolle, Key `clans.color-permission`) |
| `prismarin.clan.size.vip` | größere Clans für den Leiter (Beispiel aus `clans.size-permissions`) |

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

## Switching single social commands off

Every part of `/friend`, `/party`, `/clan` and the private messages can be switched off in `social.yml` under `sub-commands`. A switched off command disappears everywhere: from the help list, from the tab completion, from the clickable buttons in chat and from the lobby menus. Typing it shows the help instead. Some switches take a group with them:

| Key | Switch | Also hides |
| --- | --- | --- |
| `friends.sub-commands` | `requests`, `jump`, `settings` | `jump` hides the `jump` setting and the jump button in the friends menu; `settings` hides the personal settings in `/friend`, `/party` and `/clan settings` and the settings buttons in the menus |
| `party.sub-commands` | `join`, `kick`, `promote`, `transfer`, `disband`, `warp`, `chat`, `settings` | `promote` also hides `demote`; `chat` also removes `/pc` and the `chat` party setting; `join` also hides the `access` party setting |
| `clans.sub-commands` | `info`, `invite`, `join`, `kick`, `promote`, `transfer`, `disband`, `list`, `chat`, `color`, `rename`, `settings` | `invite` also hides `accept` and `deny`; `promote` also hides `demote`; `rename` also hides `tag`; `chat` also removes `/cc` |
| `messages.sub-commands` | `reply`, `toggle` | `reply` removes `/r`, `toggle` removes `/msgtoggle` |

The older switches work the same way now: a module that is off in `config.yml` (`friends`, `party`, `clans`, `private-messages`) registers no command at all, `party.follow.enabled: false` hides the `follow` party setting, and `messages.staff-spy: false` no longer registers `/socialspy`. The proxy also refuses switched off actions that arrive from a lobby menu (`DISABLED`); the API keeps working, so plugins decide for themselves. The visible lists come from `SubCommands` (tested in `SubCommandsTest`), the lobby gets them as `commands` in every `social-view`.

## Clans

Clans are persistent groups across the whole network. They live on the proxy like friends and parties, are stored in the shared document store (collection `clans`, one document per clan) and therefore work with `LOCAL`, `SQLITE`, `MYSQL`, `MARIADB` and `MONGO`. The proxy loads every clan on start and writes each change right away. With several proxies and Redis, a proxy that changes a clan tells the others on the message bus channel `prismarin:clans` and they reload that clan. Switch the whole feature off with `modules.clans: false` in `config.yml`.

### Roles and rights

Roles from low to high: member, moderator, co-leader, leader. `ClanRules` in `:api` decides every action, the proxy checks it again for every command, menu click and API call:

| Action | Default lowest role | Extra rule |
| --- | --- | --- |
| `invite` (also taking back invites) | moderator | |
| `kick` | moderator | the target has a lower role |
| `promote` | co-leader | the new role stays below the own role and below leader |
| `demote` | co-leader | the target has a higher role than member and a lower role than the actor |
| `color` | co-leader | never below co-leader, plus the permission `prismarin.clan.color` |
| `rename` (name and tag) | leader | never below co-leader |
| `settings` (who may join) | co-leader | |
| `chat` | member | |
| `transfer`, `disband` | leader | fixed |

The leader can only leave after handing over the lead, unless he is the last member; then leaving ends the clan. `/clan transfer` makes the old leader co-leader. Change the rights in `social.yml` under `clans.rights`.

### Commands

| Command | What it does |
| --- | --- |
| `/clan` | your clan with members, servers and roles, or your open invites |
| `/clan create <name> <tag>` | found a clan (permission from `clans.create-permission`, empty means everyone) |
| `/clan info [<clan>]`, `/clan list [<page>]` | any clan by name or tag, all clans sorted by size |
| `/clan invite <player>`, `accept [<clan>]`, `deny [<clan>]`, `join <clan>` | invites stay open `invite-expiry-seconds`; `join` works for open clans and open invites |
| `/clan leave`, `kick <player>`, `promote <player>`, `demote <player>`, `transfer <player>` | membership and roles |
| `/clan disband` then `/clan disband confirm` | ends the clan, the confirmation runs out after `disband-confirm-seconds` |
| `/clan color <color>` | colour of the tag: names (`red`, `gold`, `dark_aqua`, also `orange`, `pink`, German names), `&c`, `#FF8800`, `&#FF8800`, `reset` |
| `/clan rename <name>`, `/clan tag <tag>` | new name or tag |
| `/clan settings [access <invite-only\|open>]` | who may join without invite, followed by the personal settings |
| `/clan chat <message>`, `/cc <message>` | clan chat across the network; muted players cannot write, social spy (`prismarin.msg.spy`, `messages.staff-spy: true`) reads it as `[Spy · clan <name>]` |

Players choose in their personal setting `clan-invites` (everyone, friends, nobody) who may invite them. Commands are configurable in `clans.commands` and `clans.chat-commands`.

### `social.yml`, section `clans`

| Key | Default | Meaning |
| --- | --- | --- |
| `max-members` | `20` | players per clan, at most 100; `size-permissions` gives the leader more by permission |
| `name.min-length`, `name.max-length` | `3`, `16` | clan name |
| `tag.min-length`, `tag.max-length` | `2`, `5` | clan tag |
| `tag.format` | `&7[&f%name%&7]` | look of the tag, `%name%` is the tag in the clan colour, `%clan%` the full name |
| `tag.position` | `after` | `before` or `after` the player name |
| `tag.chat`, `tag.tablist` | `true`, `true` | where the tag shows; `tablist` covers tab list and name tag because Minecraft uses one team for both |
| `allowed-characters` | `[A-Za-z0-9_]+` | regular expression for names and tags |
| `blocked-names` | `admin, staff, team, mod, owner, server` | names and tags nobody may take |
| `invite-expiry-seconds`, `disband-confirm-seconds` | `300`, `30` | |
| `default-access` | `invite-only` | how new clans start |
| `create-permission`, `color-permission` | empty, `prismarin.clan.color` | |
| `rights.<action>` | see the table above | lowest role per action |
| `show-in.friends`, `show-in.party`, `show-in.player-info` | `true` | the clan tag next to names in `/friend list` and the friends menu, in `/party list` and the party menu, and a clan line in `/pinfo`; nicked players never show one |
| `commands`, `chat-commands` | `[clan]`, `[cc, clanchat]` | |
| `sub-commands.*` | `true` | see above |

A clan base (`/clan home`) is not part of this version.

### Classes

`ClanManager` (state and rules, pure, tested in `ClanManagerTest`), `ClanService` (commands, messages, management calls, pre events), `ClanStore` (document store and message bus, tested in `ClanStoreTest` on local files and SQLite), `ClanFeed` (events, relay to backends, `clan-sync`), `ClanCommands`, `ClanNaming`, `ProxyClanApi`, `SubCommands`; `:shared`: `ClanCodec`, `ClanChanges`, `ClanEvents`, `ClanColors`, `ClanTagFormat`, `ClanCalls`. `/pinfo` shows extra lines from `PlayerInfoLines`, which other proxy plugins can use too (`PlayerInfoLines.add((viewer, player) -> Optional.of(line))`).

### Channel `prismarin:moderation` for clans

| Type | Direction | Content |
| --- | --- | --- |
| `clan-sync` | proxy to every server with a member or invitee of the changed clan, and to the server a member just joined | `cause` (`change`, `sync`, `remote`), `id`, `clan` (clan JSON with `id`, `name`, `tag`, `color`, `access`, `leader`, `created`, `members[]` with `uuid`, `name`, `role`, `joined`, `data`, `invited[]`), `removed[]`, `dissolved`, `display` (`format`, `position`, `chat`, `tablist`), `rights` |
| `social` | server to proxy | actions `clan-accept`, `clan-deny`, `clan-join` (target is the clan id), `clan-leave`, `clan-kick`, `clan-promote`, `clan-demote`, `clan-transfer`, `clan-disband`, `clan-setting` (`key` `access`, `value`), view `clan` |
| `social-view` | proxy to server | `features.clans`, `features.settings`, `commands` (visible sub-commands per feature), `clan-display`, `counts.clan`, `counts.clan-invites`, `clan` (menu data with `badge`, `role`, `can-*`, `members[]`, `invited[]`, `incoming[]`), `clan` badges on friend entries and party members |
| `network-request` | server to proxy | `clan-lookup`, `clan-get`, `clan-find`, `clan-list`, `clan-act`, `clan-manage` |
| `network-event` | proxy to every server with players | every clan event, see `docs/api.md` |

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

## Votes

Module `votes` in `config.yml`. `VoteModule` loads `votes.yml`, registers `ProxyVoteService` as `Prismarin.votes()` and `/vote`. NuVotifier is an optional dependency (`@Plugin(dependencies = @Dependency(id = "nuvotifier", optional = true))`): it is compiled against `com.github.NuVotifier.NuVotifier:nuvotifier-velocity` and `nuvotifier-api` 2.7.2 from JitPack as `compileOnly`, never shaded. Only when `proxy.getPluginManager().isLoaded("nuvotifier")` and `votifier: true` the proxy registers `VotifierListener` for `VotifierEvent`, so the class that references NuVotifier is never loaded without it. Without NuVotifier votes come from plugins (`vote(name, service)`, `vote(uuid, service)`, also from backends) and from `/vote test`.

| Command | Effect | Permission |
| --- | --- | --- |
| `/vote` (`/votes`) | the sites of `votes.yml` as clickable links, then the own total and streak | none |
| `/vote test <player> [service]` | counts a test vote, also for a name that never joined; not suggested and not shown without the permission | `prismarin.vote.test` |

### What happens with a vote

1. The name is checked (`ViewerRules.plausibleName`), the service is cut to 64 characters. An online player is found by name, an offline one through the profile names.
2. For a known player `VoteLedger.count` updates `vote-players/<uuid>` in one atomic `DocumentStore.update`: total, streak (first vote of a day continues it, `streak.grace-days` like the daily rewards), best streak, last vote.
3. The reward is chosen: the site's own `reward` for votes whose service matches `sites.<id>.service`, otherwise `reward`; on the first vote of a day that reaches a `streak.milestones` day, that milestone on top.
4. The vote with its reward is stored in `vote-queue/<id>` (`player`, `name`, `name-key`, `service`, `address`, `at`, `streak`, `reward`). A name that never joined is queued with `name-key` only and waits for the first account that joins with that name.
5. `VoteReceivedEvent` fires on the proxy and is relayed as `network-event` (`vote-received`) to every server with players, the broadcast `vote.broadcast` goes to everyone when `broadcast: true`.
6. A backend with `modules.vote-rewards` claims the votes of its player (`vote-claim`): the proxy reads the queue by `player` and by `name-key`, skips votes of another account with the same name and drops votes older than `queue.keep-days`. It deletes each vote before it returns it, so parallel claims never hand one vote out twice; the answer stays below the size limit and a backend asks again until the queue is empty.

The queue lives in the proxy's storage, so offline rewards work with every storage mode and survive restarts. Old votes are pruned one minute after the start and then daily.

### `votes.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `sites.<id>.name`, `.url`, `.service`, `.enabled`, `.reward` | an `example` site with `enabled: false` | `/vote` links (the URL must start with `http`), `service` is the name the site sends through Votifier, the reward replaces the default for that site |
| `votifier` | `true` | count NuVotifier votes when the plugin is installed |
| `broadcast` | `true` | tell everyone about a vote |
| `reminder.on-join`, `after-hours`, `delay-seconds`, `interval-minutes` | `true`, `24`, `10`, `0` | remind once per visit and, with an interval above 0, regularly, when the last vote is older than `after-hours` |
| `streak.enabled`, `time-zone`, `reset-time`, `grace-days` | `true`, `server`, `00:00`, `0` | days in a row with a vote |
| `streak.milestones.<n>` | 7: 5 gems, 30: 25 gems | extra reward for the first vote of the day the streak reaches exactly `n` |
| `reward` | 100 coins and 50 XP | reward for every other vote |
| `queue.keep-days` | `30` | queued votes older than this are dropped, `0` keeps them |

Rewards use the same format as `daily-rewards.yml` (see [`core-paper.md`](core-paper.md#daily-rewards)); commands run on the console of the backend that hands the reward out, with `%service%` for the vote site. Texts under `vote.*` in the proxy's message files.
## Server queue

Module `queue` in `config.yml` (on by default). Instead of an error, a player who wants onto a full server or group waits in a queue on the proxy and is sent as soon as there is room. The proxy is the only place that sees every server, so the queue lives there; backends reach it through the network calls below.

### Which connections queue

`QueueManager` listens to `ServerPreConnectEvent` with order `LATE`, after maintenance and other plugins. Every connection of a player who is already on a server passes through it: the lobby navigator, `/play`, `/server`, `/send`, friend jumps, party warp and follow, and plugins that call `connector().connect(...)` or send `Connect` on the BungeeCord channel. A connection is let through when

- the target is the current server, under server maintenance (the maintenance listener already decided), or the queue is off for it (`enabled: false`, the lobby group by default),
- the player has `prismarin.queue.bypass`, `prismarin.queue.bypass.<server>` or `prismarin.queue.bypass.<group>`,
- the queue itself sends the player, or the player follows his party leader who just went in directly,
- nobody waits for the server or its group, neither queue is paused, and the server has room for the player and his party,
- the server is offline and `queue-when-offline` is false (Velocity reports the failed connect as before).

Otherwise the connection is cancelled and the player waits in the queue of that server. Group requests (`/play <group>`, a group in the navigator, `connector().connectToGroup(...)` on Paper and on the proxy, `/send <player> group:<name>`, `/queue join <group>`) go to the group queue through `QueueService.join` and take the first server of the group with room. Initial connections at login are never queued.

Room means `players < capacity` and, with `respect-game-state: true`, a game state that takes players (`UNKNOWN`, `WAITING`, `STARTING`). `capacity` is `max-players` of the server from the server list ping, lowered by `max-players` in `queue.yml` when that is set. Players are counted as the players this proxy has on the server plus the players the last ping saw beyond that (`Occupancy.players`), so a slot freed on this proxy counts at once and players of other proxies count after the next ping. Players the queue is sending right now are reserved until their connect finishes. Servers marked with `QueueService.joinable(server, false)` take nobody from the queue. The proxy pings every `status-refresh-seconds` while someone waits and every 10 seconds otherwise; before the first ping only `max-players` from `queue.yml` limits a server.

### Order, priority and parties

A queue is sorted by priority weight (highest first), then by the time of joining, then by a running number, so the same weight keeps first come, first served. `priorities` in `queue.yml` maps names to weights, `prismarin.queue.priority.<name>` gives the weight; the highest matching one counts. Queues that share a server (the queue of `bw-1` and the queue of group `bedwars`) take its slots in that same order. For a group the queue picks the server with room for the whole unit: starting servers first, then waiting ones, then the fullest, then by name.

With `party.together: true` a party leader queues the whole party as one unit: every member online on this proxy who is not on the target server already. Only parties that follow their leader do this (party setting `follow` and the `party.follow` rules of `social.yml`, `skip-groups` and `into-lobbies` included); otherwise the leader queues alone. The unit is only sent when there is room for all of it, members first and the leader last, so party follow finds everyone in place. With `hold-for-parties: true` a waiting party at the front holds the players behind it so free slots add up for it; `false` lets smaller units behind it go first. Members who leave the party, disconnect or switch servers drop out of the unit. `party.members-queue-alone: false` stops members from queueing on their own (`/queue join` and `/play` answer `queue.not-leader`, a direct connect to a full server fails as before). The unit uses the weight of its leader.

### Leaving

| Reason (`QueueLeftEvent.Reason`) | When |
| --- | --- |
| `LEFT` | `/queue leave`, `/leavequeue` or `QueueService.leave` |
| `SWITCHED_SERVER` | the player switched servers himself (`leave-on-server-switch: true`) |
| `DISCONNECTED` | `reconnect-grace-seconds` ran out after a disconnect, or a party member disconnected |
| `OTHER_QUEUE` | the player joined another queue (`one-queue-per-player: true`) |
| `SENT_ELSEWHERE` | with several queues per player, another queue sent him first |
| `PARTY_CHANGED` | a member left the party of the unit |
| `CLEARED` | `/queue clear` or `QueueService.clear` |
| `FAILED` | `max-send-attempts` sends in a row failed |

A player who disconnects keeps his place for `reconnect-grace-seconds`; players behind him move on meanwhile, and when he comes back in time (to this proxy) he gets `queue.reconnected` and is sent as soon as he is first in line. A failed send (the backend refused or kicked the player) puts the unit back on its old place for `retry-delay-seconds` with `queue.send-failed`.

### Display

Every `display.interval-seconds` each waiting player sees `queue.action-bar` (`display.action-bar`) and, with `display.boss-bar`, a boss bar with `queue.boss-bar` that fills up towards the front, in `boss-bar-color` and `boss-bar-style`. Paused queues use the `-paused` texts. Chat texts: `queue.joined`, `queue.joined-party`, `queue.party-queued` (members), `queue.already`, `queue.sending`, `queue.paused`, `queue.resumed`, `queue.left-*`, `queue.reconnected`.

### Commands

| Command | Effect | Permission | Switch |
| --- | --- | --- | --- |
| `/queue` | your place in every queue, the party you wait with, otherwise the help | none | |
| `/queue join <server or group>` | join a queue, sends right away when there is room | none | `join` |
| `/queue leave [<target>]`, `/leavequeue` | leave one or every queue | none | `leave` |
| `/queue list [<target>]` | every queue with size and players, or the first 15 entries of one (place, name, weight, party size, waiting time, offline marker) | `prismarin.staff.queue.list` | `list` |
| `/queue pause <target>`, `/queue resume <target>` | hold a queue, everyone keeps the place; a paused group also holds its servers and the other way round | `prismarin.staff.queue.pause` | `pause` |
| `/queue clear <target>` | remove everyone | `prismarin.staff.queue.clear` | `clear` |
| `/queue send <target>` | send the next online unit now, also when the queue is paused or the server full | `prismarin.staff.queue.send` | `send` |
| `/queue help` | the help | none | |

Targets are written as a group name, a server name or explicitly `group:<name>` and `server:<name>`; a group wins over a server of the same name. Command names come from `commands` and `leave-commands`. A switched off sub-command (`sub-commands` in `queue.yml`) and staff actions without the permission are missing from the help and the tab completion; typing them shows the help. `pause` also switches `resume`, `leave` also the `leave-commands`. The console can use the staff actions.

| Permission | Effect |
| --- | --- |
| `prismarin.queue.bypass` | skip every queue and join full servers (the backend lets the player in when its `queue` module is on) |
| `prismarin.queue.bypass.<server or group>` | the same for one server or group |
| `prismarin.queue.priority.<name>` | weight from `priorities.<name>` |
| `prismarin.staff.queue.list`, `.pause`, `.clear`, `.send` | the staff actions above |

### `queue.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `defaults.enabled` | `true` | queue connections at all |
| `defaults.max-players` | `0` | players a server takes before the queue holds the rest, `0` uses the server's own `max-players` |
| `defaults.respect-game-state` | `true` | running, ending and restarting rounds take nobody |
| `defaults.queue-when-offline` | `false` | queue even when no server of the target is online |
| `defaults.hold-for-parties` | `true` | a waiting party at the front holds the players behind it |
| `groups.<group>.<key>` | `lobby: {enabled: false}` | overrides per group of `servers.yml` |
| `servers.<server>.<key>` | none | overrides per server, they win over the group |
| `one-queue-per-player` | `true` | joining another queue leaves the old one |
| `leave-on-server-switch` | `true` | switching servers leaves the queue |
| `reconnect-grace-seconds` | `60` | place kept after a disconnect, `0` removes at once |
| `tick-millis` | `1000` | how often free slots are filled (250 to 10000) |
| `status-refresh-seconds` | `3` | ping interval while someone waits |
| `max-send-attempts` | `3` | failed sends before a player leaves the queue |
| `retry-delay-seconds` | `5` | wait after a failed or held back send |
| `priorities` | `vip: 10`, `mvp: 20` | priority names and weights |
| `party.together` | `true` | a leader queues the whole party |
| `party.members-queue-alone` | `true` | members may queue on their own |
| `display.action-bar` | `true` | action bar with place and size |
| `display.boss-bar` | `false` | boss bar with place and size |
| `display.boss-bar-color`, `display.boss-bar-style` | `blue`, `progress` | look of the boss bar |
| `display.interval-seconds` | `2` | refresh of action bar and boss bar |
| `commands`, `leave-commands` | `[queue]`, `[leavequeue]` | command names |
| `sub-commands.join`, `.leave`, `.list`, `.pause`, `.clear`, `.send` | `true` | switch single sub-commands off |

Every key is written with a comment on the first start. `/prismarinproxy reload` reads the file again (part `queue`); command names and sub-command switches need a restart.

### Several proxies

The queue is per proxy. Every proxy keeps its own queues in memory and only queues players connected to it, positions and the waiting order are not shared. Capacity is shared through the pings: a proxy counts its own players live and the players of other proxies from the last ping, so two proxies can fill the last slots of a server at the same time within one `status-refresh-seconds`; the backend then refuses the extra player and the queue puts him back on his place. A player who reconnects through another proxy loses his place, and `/queue list` only shows the queues of the proxy you are on. With one proxy all of this is exact.

### API for proxy plugins

`QueueManager` is the `QueueService` behind `prismarin.queue()` and `QueueApi.service()` (empty while the module is off). All calls complete at once on the proxy. `QueueJoinEvent` (before a player is put into a queue, also when the queue sends him right away) and `QueueSendEvent` (before a unit is sent, cancelling holds it back for `retry-delay-seconds` without counting as failure) are cancellable and stay on the proxy. `QueueJoinedEvent`, `QueueLeftEvent`, `QueueSentEvent` and `QueuePausedEvent` are relayed to every backend with players. The proxy's `connector()` is a `QueueingConnector`: `connectToGroup` goes through the group queue and returns the server only when the player was sent right away.

### Network calls

| Action | Body | Answer |
| --- | --- | --- |
| `vote-sites` | `{}` | `sites` |
| `vote-cast` | `player` or `name`, `service` | `vote` |
| `vote-stats` | `player` | `stats` (`VoteStats`) |
| `vote-pending` | `player` | `votes` |
| `vote-claim` | `player`, `name`, `limit` (1 to 10) | `votes`, removed from the queue |

Without the module the calls fail with "votes are off on the proxy". Classes: `vote/VoteModule`, `ProxyVoteService`, `VoteSettings`, `VoteCommand`, `VotifierListener`; shared `VoteLedger`, `VoteRules`, `RemoteVotes`. Tests: `VoteSettingsTest`, `ProxyVoteServiceTest`, `VoteMessagesTest`, shared `VoteLedgerTest`, `VoteRulesTest`, `RemoteVotesTest`.
| `queue-join` | `player`, `target` (`group:<name>` or `server:<name>`) | `status`, `server`, `position` |
| `queue-leave` | `player`, optional `target` | `left` |
| `queue-positions` | `player` | `positions` |
| `queue-list` | `{}` or `target` | `queues` (without entries) or `queue` (with entries, cut to the message size) |
| `queue-manage` | `op` `pause`, `resume` (`target`, `actor`), `clear`, `send` (`target`), `joinable` (`server`, `joinable`), `closed` | `changed`, `count`, `player`, `servers` |

With the module off every call fails with "the queue is off on the proxy"; `QueueCalls.join` answers `DISABLED` then and backends fall back to a direct connect.

### Classes and tests

`QueueModule` (wiring, commands, reload part), `QueueManager` (events, sending, API), `QueueBook` (queues, order, party units, grace, pause, flights and the slot planner, no Velocity types), `Occupancy` (slots, capacity, game state, player counts), `QueueRules`, `QueueSettings`, `QueueDisplay`, `QueueCommands`. Tests: `QueueBookTest` (26: priority and ties, party units with and without holding, server choice, max players and reservations, shared servers, grace and expiry, pause, server switch, one or several queues, party pruning, retries and failures, staff push, clear), `OccupancyTest` (6), `QueueSettingsTest` (8), `QueueCommandsTest` (5), `QueueMessagesTest` (4); in `:shared` `QueueCallsTest`, `QueueEventsTest`, `QueueingConnectorTest`, in `:api` `QueueTargetTest`.

## JoinMe

Module `joinme` in `config.yml` of the proxy and of every game server. A player with `prismarin.joinme` types `/joinme` on a game server, the proxy sends a clickable message to the chosen audience on the whole network, and a click brings the player to the creator. The proxy owns the active JoinMes, cooldowns, join counts and the broadcast; the game servers own the command, the cost and what happens on arrival (see [`core-paper.md`](core-paper.md#joinme)). `JoinMeModule` loads `joinme.yml`, registers `ProxyJoinMeService` as `Prismarin.joinMe()` and the network calls, and sweeps expired JoinMes every second.

### The flow step by step

1. `/joinme` on the game server: the server checks the permission, vanish, picks the JoinMe handler of a game plugin for the creator's world (if any), collects placeholders (`players`, `max_players`, `world_players`, values of the handler such as `map` or `mode`), the handler's face rows and the skin texture other players see, and asks the proxy with `joinme-check`, carried by the creator himself.
2. The proxy checks the module, the creator's server group (`create.allowed-groups`, `create.blocked-groups`), vanish, the game state (`create.joinable-state-only`, the state the game server reports), the cooldown of the best tier and whether there is anything to join at all (a handler, `join.same-server` or the world fallback of that server). It answers with the terms: cost, lifetime, max joins, audience.
3. The game server fires `JoinMeCreateEvent` (cancellable, placeholders, lifetime, max joins and audience can be changed), charges the cost through the economy of that server and sends `joinme-create`. If the proxy refuses now, the game server gives the money back.
4. The proxy checks again, fires its own `JoinMeCreateEvent` for proxy plugins, ends an older JoinMe of the same creator (`REPLACED`), stores the new one with a six character id, starts the cooldown, tells the other proxies over the message bus and sends the message with the creator's skin face (see below) to every player of the audience on this proxy. `JoinMeCreatedEvent` fires on the proxy and is relayed as `network-event` to every game server with players.
5. A click runs the hidden `/joinme join <id>` on the server the player is on. That server fires `JoinMeJoinEvent` (cancellable) and sends `joinme-join`, carried by the clicking player.
6. The proxy checks the click: the JoinMe exists and is open, it is not the own one, the player belongs to the audience's friends, party or clan filter, the join limit (counting players on their way) is not reached, `join.permission`, the target server is not known to be offline, its game state lets players join (`join.joinable-state-only`). It fires `JoinMeJoinEvent` (cancellable) and reserves a join.
7. A player on another server is connected through `Prismarin.connector()`, the same path `/play`, `/send` and the party use, so server maintenance, a full server and a later queue all apply as usual. A player who is already on the creator's server (another world of a multi world server) is not connected.
8. When the player arrives on the creator's server (`ServerPostConnectEvent`) or right away when he already is there, the proxy sends `joinme-arrival` with the JoinMe through the player's own connection. The game server runs the handler or the world fallback. The join counts now, the creator reads `joinme.joined`, and a JoinMe whose limit is reached ends with `USED_UP`.
9. When the clicking player leads a party with `follow` on, the party follows him like on every other server switch (`PartyService.follow`, unchanged). Members who arrive on the creator's server within 60 seconds get `joinme-arrival` with `party: true` as well, so a handler can put them into the same arena; they do not count as joins.
10. The JoinMe ends when it expires (`EXPIRED`), is full (`USED_UP`), is cancelled (`CANCELLED`), when the creator switches the server or goes offline with `create.end-when-creator-leaves` (`CREATOR_LEFT`) or when he starts a new one (`REPLACED`). `JoinMeExpiredEvent` fires and is relayed to the game servers, the creator reads `joinme.ended.<reason>`.

A clicked player is expected for `join.arrival-timeout-seconds`; a connection that fails releases the reserved join and the player reads `joinme.connect-failed`.

### Preview

`/joinme preview` on a game server sends `joinme-preview` with the same draft as `joinme-check`, carried by the creator, so the call always reaches the creator's own proxy. `ProxyJoinMeService.preview` then:

1. takes the creator's spot like a creation (server, group, world, game state) but checks neither the group rules, vanish, the game state, the cooldown nor the cost; lifetime and max joins come from the creator's tiers so `%time%` and `%max_joins%` show what a real JoinMe would get,
2. fires `JoinMePreviewEvent` for proxy plugins (placeholders and face rows, like `JoinMeCreateEvent`),
3. builds a JoinMe with the id `preview` that is never stored, shared with other proxies or relayed, and
4. sends `joinme.preview-header` and then the broadcast exactly as the audience gets it (face block or `joinme.broadcast`, rows of `face.rows`, the skin face of the texture the game server sent, so a nick's skin with a nick, title and sound when switched on) only to the creator. The creator's name shows as everybody without `prismarin.nick.realname` sees it. The message has no click and the hover `joinme.preview-hover`, so `[ Join ]` does nothing.

The answer is `SUCCESS` without a JoinMe, or `OFFLINE` when the creator is not on this proxy. No cooldown starts, an open JoinMe of the creator stays as it is, no `JoinMeCreateEvent` or `JoinMeCreatedEvent` fires.

### Without Redis and with several proxies

Nothing in the flow needs Redis: the calls travel as plugin messages through the creator or the clicking player, the arrival through the arriving player. The message bus channel `prismarin:joinme` is only there for several proxies. With Redis each proxy keeps a copy of every JoinMe: `created` (the JoinMe and its world fallback), `ended` (`id`, `reason`) and `joined` (`id`, `delta`) keep them in step, each proxy sends the message to its own players, a click is handled by the clicking player's proxy and its join is added on all of them. Cooldowns travel with `created`. Only the proxy that created a JoinMe relays its events to the game servers and talks to the creator. Two clicks at the same moment on two proxies can go one join over the limit. Several proxies without Redis do not share JoinMes: each one only knows and reaches its own players.

### `joinme.yml`

| Key | Default | Meaning |
| --- | --- | --- |
| `create.lifetime-seconds` | `120` | how long a JoinMe can be clicked (10 to 86400) |
| `create.max-joins` | `0` | joins per JoinMe, `0` is unlimited |
| `create.allowed-groups`, `create.blocked-groups` | `[]`, `[lobby]` | server groups of `servers.yml` where `/joinme` works; empty allowed means every group |
| `create.joinable-state-only` | `true` | only while the game state is `UNKNOWN`, `WAITING` or `STARTING` |
| `create.end-when-creator-leaves` | `true` | ends the JoinMe when the creator switches the server or goes offline |
| `cooldowns.<tier>` | `default: 300`, `premium: 180`, `youtuber: 60` | seconds between two JoinMes; tier permission `prismarin.joinme.cooldown.<tier>`, the shortest wins |
| `costs.<tier>.currency`, `.amount` | `default`: `coins`, `0` | cost in any currency of `economy.currencies`; `prismarin.joinme.cost.<tier>`, the lowest amount wins |
| `lifetime-tiers.<tier>` | `youtuber: 300` | longer lifetime in seconds; `prismarin.joinme.lifetime.<tier>`, the longest wins |
| `max-joins-tiers.<tier>` | none | more joins; `prismarin.joinme.joins.<tier>`, the most wins, `0` is unlimited |
| `join.same-server` | `true` | fallback without a handler: send the player to the creator's server; `false` means only players already on that server can follow (multi world servers) |
| `join.joinable-state-only` | `true` | refuses clicks while the target's game state does not let players join |
| `join.permission` | empty | permission to click, empty means everybody |
| `join.arrival-timeout-seconds` | `300` | how long a clicked player is expected |
| `join.notify-creator` | `true` | `joinme.joined` for the creator |
| `audience.scope` | `groups` | `groups` or `network` |
| `audience.groups` | `[lobby]` | server groups for `groups` |
| `audience.only` | `[]` | `friends`, `party` and or `clan` of the creator narrow the audience; empty means everybody in scope |
| `audience.permission` | empty | permission to get the message |
| `audience.skip-same-world` | `true` | players already in the creator's world get no message |
| `broadcast.title` | `false` | also shows `joinme.title` and `joinme.subtitle` |
| `broadcast.sound`, `volume`, `pitch` | `entity.experience_orb.pickup`, `1.0`, `1.2` | sound for the message, empty plays none |

Every tier called `default` applies to everybody. The permissions are checked on the proxy, so they need LuckPerms on Velocity or `trust-backend-operators` in `admin.yml` for operators; without them everybody gets the `default` tiers. `/joinme` itself (`prismarin.joinme`) is checked on the game server.

### The skin face in chat

By default the message is a block of 8 chat lines: the creator's face as 8 x 8 coloured blocks (`face.symbol`, `█`), every pixel in its exact colour (`TextColor`, old clients get the nearest legacy colour through ViaVersion), with the hat layer (skin area x 40 to 47, y 8 to 15) laid over the face (x 8 to 15, y 8 to 15) by its alpha. On old 64 x 32 skins a hat area that is completely opaque is ignored, as the game does. The texts stand to the right of the rows listed under `face.rows`: row 2 `joinme.face.title` (creator and game), row 4 `joinme.face.details` (server, map, players), row 5 `joinme.face.state` (state and time left), row 7 `joinme.face.button` (`[ Join ]`). Rows 9 to 16 stand below the face. An empty line before and after the block is on by default. The whole block is clickable and has the `joinme.broadcast-hover` text.

1. The game server sends the value of the `textures` property of the creator's profile as other players see it there; with a nick that changes the skin this is the nick's skin, with the nick skin `#default` it is none.
2. The proxy decodes it (base64 JSON), accepts only `http(s)://textures.minecraft.net/texture/<hash>` without user, port, query or fragment and downloads exactly `https://textures.minecraft.net/texture/<hash>` with `java.net.http.HttpClient`, no redirects, at most 256 KB, timeout `face.download-timeout-millis`. Nothing else is sent. The download already starts when the JoinMe is checked, on a worker thread, never on an event thread.
3. The computed face is cached by texture hash: in memory (`face.cache-size`, least recently used faces go first) and with `face.disk-cache` as `skin-faces/<hash>.face` (64 hex colours) in the plugin folder. Parallel requests for one skin share one download. A failed download is not cached.
4. Without a skin (offline mode, `#default` nick skin, failed download) the built-in Steve face is used, or Alex for slim skins and for UUIDs whose hash is odd. With `face.without-skin: text` the one line `joinme.broadcast` is sent instead; `face.enabled: false` always sends it.

A game plugin changes rows through `JoinMeHandler.lines(spot)`, any plugin through `JoinMeCreateEvent.lines()` / `line(row, text)` on the game server or on the proxy: a value is a message key (rendered in each viewer's language) or a MiniMessage text with the same placeholders; an empty value removes a configured row.

`prismarin.skinFaces()` (`SkinFaces`) is registered with the module and renders faces for other plugins too, see [`docs/api.md`](../api.md#skin-faces-in-chat). On the proxy `face(player)` uses the player's login profile, a nicked player gets the default face so the real skin never shows.

| Key | Default | Meaning |
| --- | --- | --- |
| `face.enabled` | `true` | the face block, `false` sends `joinme.broadcast` |
| `face.symbol` | `█` | character for one pixel (one or two characters) |
| `face.without-skin` | `default` | `default` (Steve or Alex) or `text` |
| `face.blank-line-before`, `face.blank-line-after` | `true`, `true` | empty chat line around the block |
| `face.gap` | one space | between the face and the text |
| `face.rows.<1-16>` | `2`, `4`, `5`, `7` as above | message key or text per row |
| `face.cache-size` | `512` | faces in memory |
| `face.disk-cache` | `true` | faces in `skin-faces/` |
| `face.download-timeout-millis` | `3000` | 250 to 30000 |

### Texts

`joinme.face.title`, `.details`, `.state`, `.button` (rows next to the face), `joinme.broadcast` (two lines with the creator, the game and a `[Join]` button, the whole message is clickable), `joinme.broadcast-hover`, `joinme.title`, `joinme.subtitle`, `joinme.preview-header` (above a preview), `joinme.preview-hover` (hover of a preview instead of `broadcast-hover`), `joinme.joined`, `joinme.connect-failed`, `joinme.ended.expired`, `.used-up`, `.cancelled`, `.creator-left` and `joinme.state.<state>`. Placeholders: `%creator%` (the nick for everybody without `prismarin.nick.realname`), `%server%`, `%server_display%`, `%group%` (display name of the group), `%group_id%`, `%world%`, `%state%`, `%players%`, `%max_players%` (live from the network status when known), `%world_players%`, `%joins%`, `%max_joins%`, `%joins_left%`, `%time%` (time left as `m:ss`), `%seconds%`, `%id%`, `%map%` (a handler's value, otherwise the world), `%mode%` (empty unless a handler sets it) and every other value a handler or plugin adds. Unknown placeholders stay as they are. `joinme.joined` also has `%player%`.

### Network calls and channel

| Action | Body | Answer |
| --- | --- | --- |
| `joinme-check` | `draft` (`spot` with `creator`, `server`, `group`, `world`, `state`; `shown-name`, `handler`, `world-fallback`, `placeholders`, `lifetime`, `max-joins`, `audience`, `ignore-cooldown`, `free`, `look`) | `terms` (`status`, `wait`, `currency`, `cost`, `lifetime`, `max-joins`, `audience`, `same-server`) |
| `joinme-create` | `draft` | `result` (`status`, `wait`, `joinme`) |
| `joinme-list` | `viewer` (optional) | `joinmes`, `truncated` |
| `joinme-find` | `id` | `joinme` or nothing |
| `joinme-join` | `player`, `id` | `result` |
| `joinme-cancel` | `id` or `creator` | `cancelled` |
| `joinme-preview` | `draft` (as `joinme-check`) | `result` (`status`) |

`joinme-check` and `joinme-create` also carry `look` in the draft: `texture` (value of the `textures` property, at most 8192 base64 characters) and `lines` (row to message key or text). The bus message `created` carries the same `look`, so every proxy draws the same face.

On `prismarin:moderation` the proxy sends `joinme-arrival` (`uuid`, `joinme`, `party`) to the arriving player's server and relays `joinme-created` and `joinme-expired` as `network-event`. Without the module every call fails with "joinme is off on the proxy", which the game server shows as `DISABLED`.

Classes: `joinme/JoinMeModule`, `ProxyJoinMeService`, `JoinMeBroadcaster`, `JoinMeSettings`, `JoinMeRules`, `JoinMeRegistry`, `PendingArrivals`, `AudienceSelector`, `Tiers`; shared `JoinMeCodec`, `JoinMeDraft`, `JoinMeTerms`, `JoinMeLook`, `JoinMeRoute`, `JoinMePlaceholders`, `JoinMeCalls`, `skin/SkinFaceService`, `SkinTextures`, `FacePixels`, `FaceRows`, `DefaultFaces`. Tests: `JoinMeRulesTest`, `JoinMeRegistryTest`, `AudienceSelectorTest`, `JoinMeSettingsTest`, `JoinMeMessagesTest`, `ProxyJoinMePreviewTest` (only the creator gets the preview, nothing is stored, no cooldown, an open JoinMe and its cooldown stay, offline creators get nothing); shared `FacePixelsTest`, `SkinTexturesTest`, `SkinFaceServiceTest`, `FaceRowsTest`.
