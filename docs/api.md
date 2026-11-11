# PRISMARIN API

Everything PRISMARIN does for a network is reachable from other plugins through the `:api` module: players, profiles, currencies, storage, friends, parties, nicks, reports, punishments, the audit log, maintenance, staff alerts, game states and the server directory. A game plugin or an anticheat never needs its own database, its own nick handling or its own connection to the proxy.

This page lists every service, how to get it on Paper and on Velocity, the events, and short examples. The example plugin in [`example-paper`](../example-paper) uses most of it in small classes. Module details live in [`docs/modules`](modules).

## Adding the dependency

The API is a small jar without platform code. Paper and Velocity already ship Adventure and Gson, so the jar declares no dependencies of its own. Always add it as `compileOnly`, PrismarinCore provides the classes at runtime.

### GitHub Packages

```kotlin
repositories {
    maven("https://maven.pkg.github.com/LeonManuel0705/prismarin") {
        credentials {
            username = providers.gradleProperty("gpr.user").get()
            password = providers.gradleProperty("gpr.key").get()
        }
    }
}

dependencies {
    compileOnly("net.prismarin:prismarin-api:1.0.0")
}
```

GitHub Packages needs a personal access token with `read:packages`, even for public packages.

```xml
<dependency>
    <groupId>net.prismarin</groupId>
    <artifactId>prismarin-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### JitPack

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.LeonManuel0705:prismarin:v1.0.3")
}
```

JitPack builds the tag with `jitpack.yml` (Java 21, only `:api` is installed). Because that is the only artifact, JitPack publishes it as `com.github.LeonManuel0705:prismarin:<tag>`.

### Building it yourself

```bash
./gradlew :api:publishToMavenLocal
```

Then use `mavenLocal()` and `net.prismarin:prismarin-api:1.0.0`. Maintainers publish to GitHub Packages with `GITHUB_ACTOR` and `GITHUB_TOKEN` set: `./gradlew :api:publishApiPublicationToGitHubPackagesRepository`. The publication contains the jar, a sources jar and a POM with the MIT license.

## Getting the API

### Paper

```yaml
depend: [PrismarinCore]
```

```java
Prismarin prismarin = PrismarinProvider.get();
```

The same instances are registered in the Bukkit `ServicesManager`: `Prismarin`, `PlayerService`, `FriendService`, `PartyService`, `NickService` (when the nick module is on), `DataStorage`, `AuditService`, `MaintenanceService`, `NotificationService`, `ReportService` and `PunishmentTemplates`.

```java
NotificationService notifications = Bukkit.getServicesManager().load(NotificationService.class);
```

Paper modules on PrismarinCore's child injector can also `@Inject` `PlayerService`, `ProfileService`, `EconomyService`, `EventBus` and the other bound services (see [`conventions.md`](conventions.md)).

### Velocity

```java
@Plugin(id = "myplugin", dependencies = {@Dependency(id = "prismarin-core")})
public final class MyPlugin {

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        Prismarin prismarin = PrismarinProvider.get();
    }
}
```

`PrismarinProvider.isAvailable()` tells whether PrismarinCore is enabled. Every service below hangs off the `Prismarin` instance; the static holders (`PlayerApi`, `SocialApi`, `ModerationApi`, `NickApi`, `StorageApi`, `AdminApi`) return the same objects for code that has no `Prismarin` at hand.

## Entry points

| Call | Type | Paper | Velocity |
|:---|:---|:---|:---|
| `serverId()` | `String` | `server-id` of this server | `server-id` of the proxy |
| `branding()` | `Branding` | yes | yes |
| `profiles()` | `ProfileService` | yes | yes |
| `economy()` | `EconomyService` | yes, a stub when `systems.economy` is off | yes |
| `locale()` | `LocaleService` | yes | yes |
| `messageBus()` | `MessageBus` | Redis or in process | Redis or in process |
| `servers()` | `ServerDirectory` | `servers.yml` | `servers.yml` plus Velocity servers |
| `serverStatus()` | `ServerStatusService` | yes | yes |
| `gameState()` | `GameStateService` | state of this server | not used |
| `connector()` | `NetworkConnector` | yes | yes |
| `punishments()` | `PunishmentService` | yes | yes |
| `events()` | `EventBus` | events of this server | events of the proxy |
| `enabledSystems()`, `systemEnabled(name)` | `Set<String>`, `boolean` | yes | yes |
| `players()` | `PlayerService` | yes | yes |
| `friends()`, `parties()` | `Optional<FriendService>`, `Optional<PartyService>` | yes | when the module is on |
| `reports()` | `Optional<ReportService>` | forwarded to the proxy | when `modules.reports` is on |
| `punishmentTemplates()` | `Optional<PunishmentTemplates>` | forwarded to the proxy | when `modules.punishments` is on |
| `nicks()` | `Optional<NickService>` | when `modules.nick` is on | empty |
| `storage()` | `DataStorage` | yes | yes |
| `audit()` | `AuditService` | yes | yes |
| `maintenance()` | `Optional<MaintenanceService>` | forwarded to the proxy | when `maintenance` or `server-maintenance` is on |
| `notifications()` | `NotificationService` | forwarded to the proxy | yes |

All I/O returns a `CompletableFuture` that completes on a worker thread. Switch back to the main thread before touching Bukkit (`Bukkit.getScheduler().runTask(plugin, ...)`); sending Adventure messages is safe from any thread.

## Players

`PlayerService` answers everything about a player with nicks, vanish and the chosen language already applied.

```java
PlayerService players = prismarin.players();
String shown = players.displayName(viewer, target);
RankDisplay rank = players.rank(viewer, target);
Locale language = players.locale(target);
players.resolve(viewer, typedName).thenAccept(found -> found.ifPresent(player -> log(player.shownName())));
players.attributes().register(PlayerAttribute.of("bedwars_wins", player -> String.valueOf(wins(player.uuid()))));
players.ranks().register(uuid -> premium(uuid) ? Optional.of(PREMIUM_RANK) : Optional.empty(), 10);
```

`displayName` shows the real name only to the player himself and to holders of `prismarin.nick.realname`. Attributes appear as `%prismarin_data_<key>%` and in the lobby profile, rank sources override the LuckPerms rank in chat, tablist and sidebar. Details: [`core-paper.md`](modules/core-paper.md#players-names-nicks-language-server-and-world).

## Profiles, levels and XP

```java
prismarin.profiles().getCached(uuid).ifPresent(profile -> log(profile.getLevel()));
prismarin.profiles().modify(uuid, profile -> profile.addXp(50));
```

`getCached` reads the profile of an online player without I/O. `modify` loads the profile when needed, applies the change in order with other changes of that player and saves it. `addXp` levels up automatically. Check `systemEnabled(PrismarinSystems.LEVELS)` before showing levels.

## Economy

```java
Currency coins = prismarin.economy().primaryCurrency();
prismarin.economy().deposit(uuid, coins, 50).thenAccept(result -> log(result.success() + " " + result.newBalance()));
prismarin.economy().currency("gems").ifPresent(gems -> prismarin.economy().withdraw(uuid, gems, 3));
```

Every transaction is atomic in the database. With `systems.economy: false` every call fails with `SYSTEM_DISABLED`; check `systemEnabled(PrismarinSystems.ECONOMY)` first.

## Storage for plugins

`storage()` gives each plugin its own namespace in PRISMARIN's configured storage (JSON files, SQLite, MySQL or MariaDB, MongoDB). A game plugin stores its stats there and gets the same backend, the same backups and `/prismarin migrate` for free.

```java
DataNamespace data = prismarin.storage().namespace("bedwars");

TypedCollection<Stats> stats = data.collection("stats", Stats.class);
stats.save(uuid.toString(), new Stats(12, 30));
stats.find(uuid.toString()).thenAccept(found -> found.ifPresent(value -> log(value.wins())));
stats.findBy("wins", "12").thenAccept(list -> log(list.size()));
stats.list().thenAccept(all -> all.forEach((id, value) -> log(id + " " + value)));

DataCollection raw = data.collection("maps");
raw.save("castle", "{\"author\":\"Leon\",\"size\":4}");
```

| Rule | Value |
|:---|:---|
| Namespace | `[a-z0-9][a-z0-9-]{1,23}`, lowercased, `prismarin` is reserved |
| Collection | `[a-z0-9][a-z0-9-]{0,31}`, lowercased |
| Document id | 1 to 128 characters, case sensitive, any text |
| Physical name | `ext_<namespace>_<collection>`; PRISMARIN's own collections never start with `ext_`, and namespaces cannot contain `_`, so two plugins never share a collection |
| `findBy(field, value)` | matches top level primitive fields as text (`"wins"` and `"12"` match `{"wins":12}`) |
| Typed collections | Gson, records work; pass your own `Gson` with `collection(name, type, gson)` |
| Sharing | with `MONGO` or `MYSQL` every server sees the same documents, with `LOCAL` or `SQLITE` each server has its own |
| Migration | `/prismarin migrate` and `/prismarinproxy migrate` move every `ext_` collection along |

Invalid names and ids throw `IllegalArgumentException` right away. Writes are last write wins; keep one writer per document (for example the server the player is on) when several servers could change it.

## Friends and parties

```java
prismarin.parties().flatMap(parties -> parties.partyOf(uuid)).ifPresent(party -> teams.join(party.memberIds()));
prismarin.players().areFriends(first, second).thenAccept(together -> { });
prismarin.friends().ifPresent(friends -> friends.sendRequest(sender, target));
```

On a backend the party mirror answers without waiting; actions go to the proxy as the acting player. Details: [`core-paper.md`](modules/core-paper.md#friends-and-parties-for-backend-plugins) and [`core-velocity.md`](modules/core-velocity.md).

## Nicks

```java
prismarin.nicks().ifPresent(nicks -> {
    boolean nicked = nicks.isNicked(uuid);
    nicks.nickRandom(uuid).thenAccept(result -> log(result.status() + " " + result.name()));
    nicks.markInRound(uuid, true);
});
```

`NickService` lives on the backends (`modules.nick`). For names shown to other players use `players().displayName(viewer, target)`, it already respects nicks and the real name permission.

## Reports and punishment templates

```java
prismarin.reports().ifPresent(reports -> reports.file(anticheatId, "AntiCheat", suspect, "hacking Reach 4.2")
        .thenAccept(result -> log(result.report().id() + " " + result.outcome())));

prismarin.punishmentTemplates().ifPresent(templates -> templates.preview(suspect, "hacking")
        .thenCompose(preview -> templates.punish(suspect, "hacking", "AntiCheat", "automatic"))
        .thenAccept(punishment -> log(punishment.type() + " until " + punishment.expiresAt())));
```

On the proxy both services run directly. On a backend they forward every call to the proxy and return the proxy's answer, so a Paper anticheat files reports and applies templates exactly like a proxy plugin.

| Backend behaviour | |
|:---|:---|
| `file`, `open`, `openAgainst`, `find`, `claim`, `release`, `close`, `preview`, `punish` | forwarded, the future fails with the proxy's message when the module is off or the call is refused |
| `reasons()`, `templates()`, `template(id)`, `claimTimeout()` | a cached copy of the proxy's lists, refreshed at most once a minute and after a player joins; empty until the first answer |
| `registerReason`, `registerTemplate` | sent to the proxy and sent again when the proxy lost them after a restart; ids the proxy refuses (configured ids) are dropped |
| `registerAction` | throws `UnsupportedOperationException`: action handlers run on the proxy, register them from a Velocity plugin |

`ReportFiledEvent` fires on the proxy and, relayed by the proxy, on every backend with players online. Details: [`core-velocity.md`](modules/core-velocity.md#reports-ban-lists-and-the-moderation-api).

## Punishments

`punishments()` is the raw `PunishmentService`: `punish`, `active`, `history`, `inForce`, `revoke`. It writes to the shared storage directly and does not kick or notify; for the full flow (escalation, kick, staff message, audit) use `punishmentTemplates().punish`.

## Audit log

```java
prismarin.audit().record("AntiCheat", "anticheat.flag", suspectName, "Reach 4.2 on bw-1");
prismarin.audit().latest(suspectName, 20).thenAccept(entries -> entries.forEach(entry -> log(entry.action())));
```

Entries show up in `/audit` on the proxy. `action` matches `[a-z0-9][a-z0-9_.:-]{0,63}`, the actor has 1 to 64 characters, the target up to 128, details up to 1024. With `MONGO` or `MYSQL` a backend writes into the shared storage itself; with `LOCAL` or `SQLITE` it sends the entry to the proxy and keeps it locally only when no route exists. `latest` returns at most 100 entries.

## Maintenance

```java
prismarin.maintenance().ifPresent(maintenance -> {
    maintenance.state().thenAccept(state -> log(state.network() + " " + state.targets()));
    maintenance.setServer("bw-1", true, "BedwarsReset");
    maintenance.setGroup("bedwars", false, "BedwarsReset");
    maintenance.setNetwork(true, "Leon");
});
```

`MaintenanceState` holds the network switch and the blocked servers and groups (`blocks(server, group)`). Every switch returns whether something changed, is written to the audit log and fires `MaintenanceChangedEvent` on the proxy. `/maintenance` uses the same service. On the proxy the service exists when `modules.maintenance` or `modules.server-maintenance` is on; `setNetwork` needs the first, `setServer` and `setGroup` the second, otherwise the future fails with the reason. Backends always get the service and forward to the proxy.

## Staff alerts, staff chat and broadcasts

```java
NoticeText text = NoticeText.of("%player% may fly (%check%)")
        .and("de", "%player% fliegt vielleicht (%check%)")
        .with("player", suspectName)
        .with("check", "Fly A");
prismarin.notifications().notifyStaff(StaffAlert.of("AntiCheat", text).permission("anticheat.alerts"))
        .thenAccept(delivery -> log(delivery));
prismarin.notifications().staffChat("AntiCheat", suspectName + " was kicked for Fly");
prismarin.notifications().broadcast(Broadcast.group("bedwars", NoticeText.of("<gold>Double coins this hour")));
```

| Type | What it holds |
|:---|:---|
| `NoticeText` | MiniMessage or `&` texts per language (`*` for any language, then the exact tag, the language, `en`) and placeholders that are inserted as plain text, so player names cannot inject tags |
| `StaffAlert` | source label shown in brackets, permission (default `prismarin.staff.alerts`), text; shown to every player with the permission and the proxy console, and fired as `StaffAlertEvent` on the proxy |
| `StaffChat` | author and message, shown in the `/sc` format to `prismarin.staff.chat` |
| `Broadcast` | scope `all`, `server:<name>` or `group:<name>` and a text, shown like `/alert` and written to the audit log |
| `Delivery` | `DELIVERED` once the proxy showed it, `NO_ROUTE` when a backend had neither a player online nor Redis |

Texts are rendered per receiver in the language PRISMARIN knows for him. Limits: 1024 characters per text, 16 languages, 16 placeholders with up to 256 characters.

## Game state, servers and connecting

```java
prismarin.gameState().state(GameState.WAITING, "3/8");
prismarin.gameState().state(GameState.RUNNING);
prismarin.servers().byGroup("bedwars").forEach(server -> log(server.name()));
prismarin.connector().connectToGroup(uuid, "bedwars");
prismarin.serverStatus().status("bw-1").ifPresent(status -> log(status.players() + "/" + status.maxPlayers()));
```

Lobbies see the game state through the server list ping even without Redis. Details: [`core-paper.md`](modules/core-paper.md).

## Message bus, locale and branding

```java
prismarin.messageBus().subscribe("bedwars:queue", message -> handle(message.payloadJson()));
prismarin.messageBus().publish("bedwars:queue", "{\"arena\":\"castle\"}");
Component line = prismarin.locale().format("%brand_prefix%<gray>Ready</gray>");
```

Without Redis the bus only reaches the same server.

## How backend calls reach the proxy

Forwarded services (reports, templates, maintenance, notifications, the audit log on unshared storage) send a `network-request` on the plugin channel `prismarin:moderation` through a player online on that server; the proxy answers with `network-answer` through the same player. When nobody is online and Redis is on, the request goes over the message bus channel `prismarin:network` and the answer comes back on `prismarin:network-answer`.

- The proxy only accepts requests from a `ServerConnection`, never from a client, and only when the carrying player is the one named in the message.
- Every body is validated on the proxy (names, lengths, permissions, scopes); a refused call answers with a readable error that ends up as the future's exception.
- Messages stay below 30 000 bytes. Long lists (`open()`, `latest`) are cut and carry `truncated: true`.
- Calls time out after five seconds. Punishing a player prefers another player as carrier, because a ban disconnects the target.
- A backend without players and without Redis cannot reach the proxy: notifications return `NO_ROUTE`, audit entries stay local, the other calls fail with `NoRouteException`.

## Events

Subscribe with `prismarin.events().subscribe(Type.class, handler)` and keep the `Subscription` to unsubscribe in `onDisable`. Handlers run on the thread that fires the event, often an I/O thread.

| Event | Package | Fires on |
|:---|:---|:---|
| `ProfileLoadedEvent`, `ProfileUnloadedEvent` | `profile` | the server that loaded or saved the profile |
| `BalanceChangedEvent` | `economy` | the server that booked the transaction |
| `ServerChangedEvent` | `player` | the proxy for every switch, a backend when a player arrives there |
| `LocaleChangedEvent` | `player` | proxy and backends |
| `NickChangedEvent` | `nick` | the backend that changed the nick |
| `StaffModeChangedEvent` | `staff` | the backend |
| `PunishmentIssuedEvent`, `PunishmentRevokedEvent` | `moderation` | the server that issued or revoked it, usually the proxy |
| `ReportFiledEvent` | `moderation` | the proxy, relayed to backends with players |
| `ReportMergedEvent`, `ReportClaimedEvent`, `ReportReleasedEvent`, `ReportClosedEvent` | `moderation` | the proxy |
| `FriendRequestEvent`, `FriendAddedEvent`, `FriendRemovedEvent` | `social` | the proxy |
| `PartyCreatedEvent`, `PartyInviteEvent`, `PartyJoinedEvent`, `PartyLeftEvent`, `PartyRoleChangedEvent`, `PartySettingsChangedEvent`, `PartyDisbandedEvent` | `social` | the proxy, and backends with a member or invitee online |
| `MaintenanceChangedEvent` | `admin` | the proxy |
| `StaffAlertEvent` | `admin` | the proxy |

## The example plugin

`example-paper` is built with the project but not part of `dist`. It depends only on the API and Paper:

| Class | Shows |
|:---|:---|
| `ExamplePlugin` | getting `Prismarin`, wiring, cleaning up subscriptions |
| `PlayerLookup` | `resolve`, `displayName`, `rank`, party and friend lookups |
| `Rewards` | `economy().deposit` and XP through `profiles().modify` |
| `StatsStore`, `Stats` | a typed collection in `storage()` |
| `StatsDisplay` | a player attribute and a rank source |
| `Moderation` | filing a report, previewing and applying a template, audit entry plus staff alert |
| `EventLog` | `ProfileLoadedEvent`, `BalanceChangedEvent`, `NickChangedEvent`, `ReportFiledEvent`, `ServerChangedEvent` |
| `RoundState` | setting the game state |
| `ExampleCommand`, `Texts` | `/example ...` with texts in the player's language |

## Module documentation

- [`modules/api.md`](modules/api.md): class structure of `:api`
- [`modules/shared.md`](modules/shared.md): storage, message bus, network calls
- [`modules/core-paper.md`](modules/core-paper.md): backend features and their APIs
- [`modules/core-velocity.md`](modules/core-velocity.md): proxy features, reports, punishments, maintenance
- [`modules/lobby.md`](modules/lobby.md): the optional lobby
