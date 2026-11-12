# PRISMARIN API

Everything PRISMARIN does for a network is reachable from other plugins through the `:api` module: players, profiles, currencies, storage, stats and leaderboards, daily rewards, votes, friends, parties, nicks, reports, punishments, the chat filter, alt accounts, the audit log, maintenance, staff alerts, game states, the server queue and the server directory. A game plugin or an anticheat never needs its own database, its own nick handling or its own connection to the proxy.

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
    compileOnly("com.github.LeonManuel0705:prismarin:v1.1.0")
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

The same instances are registered in the Bukkit `ServicesManager`: `Prismarin`, `PlayerService`, `FriendService`, `PartyService`, `NickService` (when the nick module is on), `DataStorage`, `AuditService`, `MaintenanceService`, `NotificationService`, `ReportService`, `PunishmentTemplates`, `AltService`, `VoteService`, `QueueService`, `ChatFilterService` (when the chat filter module is on), `StatsService` (when `modules.stats` is on) and `DailyRewardService` (when `modules.daily-rewards` is on).

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

`PrismarinProvider.isAvailable()` tells whether PrismarinCore is enabled. Every service below hangs off the `Prismarin` instance; the static holders (`PlayerApi`, `SocialApi`, `ModerationApi`, `NickApi`, `QueueApi`, `StorageApi`, `AdminApi`) return the same objects for code that has no `Prismarin` at hand.

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
| `clans()` | `Optional<ClanService>` | yes, mirror plus calls to the proxy | when `modules.clans` is on |
| `reports()` | `Optional<ReportService>` | forwarded to the proxy | when `modules.reports` is on |
| `punishmentTemplates()` | `Optional<PunishmentTemplates>` | forwarded to the proxy | when `modules.punishments` is on |
| `chatFilter()` | `Optional<ChatFilterService>` | this server's filter when `modules.chat-filter` is on | the proxy's filter for private and party messages when `modules.chat-filter` is on |
| `alts()` | `Optional<AltService>` | forwarded to the proxy | when `modules.alts` is on |
| `nicks()` | `Optional<NickService>` | when `modules.nick` is on | empty |
| `stats()` | `Optional<StatsService>` | when `modules.stats` is on | empty |
| `dailyRewards()` | `Optional<DailyRewardService>` | when `modules.daily-rewards` is on | empty |
| `votes()` | `Optional<VoteService>` | forwarded to the proxy | when `modules.votes` is on |
| `queue()` | `Optional<QueueService>` | yes, forwarded to the proxy | when `modules.queue` is on |
| `joinMe()` | `Optional<JoinMeService>` | when `modules.joinme` is on: the command side, handlers, calls to the proxy | when `modules.joinme` is on: the JoinMes, cooldowns and broadcast |
| `skinFaces()` | `Optional<SkinFaces>` | when `modules.joinme` is on | when `modules.joinme` is on |
| `cosmetics()` | `Optional<CosmeticService>` | when `modules.cosmetics` is on: catalogue, ownership, menus, particles, hats, effects | empty |
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

## Stats and leaderboards

```java
prismarin.stats().ifPresent(stats -> {
    stats.increment(winner, "bedwars.wins");
    stats.increment(killer, "bedwars.kills", 1);
    stats.record(runner, "parkour.time", millis);
    stats.set(player, "bedwars.level", 12);

    stats.top("bedwars.wins", StatPeriod.WEEKLY, 10).thenAccept(board -> board.entries().forEach(entry ->
            log(entry.rank() + ". " + entry.name() + " " + entry.value())));
    stats.rank(player, "bedwars.wins", StatPeriod.ALL_TIME).thenAccept(rank -> rank.ifPresent(this::show));
    stats.cached(player).ifPresent(values -> log(values.value("bedwars.wins", StatPeriod.MONTHLY)));
    stats.define(StatDefinition.of("bedwars.wins", "Wins").withProfile(true));
});
prismarin.events().subscribe(StatRecordEvent.class, event -> announce(event.player(), event.key(), event.value()));
```

| Call | What it does |
|:---|:---|
| `increment(player, key[, amount])` | adds to the all time value and the current weekly and monthly totals, atomic in the storage |
| `set(player, key, value)` | sets the all time value; weekly and monthly move by the same difference |
| `record(player, key, value)` | keeps the better value (`highest` or `lowest` from `stats.yml`) for all time, the week and the month; `StatRecordEvent` when it improved |
| `stats(player)`, `value(player, key, period)` | read one player's `PlayerStats` |
| `cached(player)` | players on this server without I/O, used by placeholders and menus |
| `top(key, period, size)`, `cachedTop(key, period)` | `Leaderboard` with `LeaderboardEntry(rank, player, name, value)`; names respect nicks and vanish |
| `rank(player, key, period)`, `cachedRank(...)` | position of a player, also below the cached list |
| `periods()`, `periodId(period)` | enabled periods and the id of the running week (`2026-11-09`) or month (`2026-11`) |
| `definition(key)`, `definitions()`, `define(definition)`, `format(key, value, locale)` | shown name, `StatFormat` (`number`, `plain`, `duration`, `millis`, `decimal`) and `StatOrder`; `stats.yml` wins over `define` |
| `game(id)`, `games()`, `defineGame(game)` | `StatGame` metadata of a game: name (one text or one per language), icon material for menus, colour, order, featured stats; `stats.yml` wins field by field |
| `registerSource(source)`, `source(game)` | a `StatSource` for a game whose plugin keeps its stats in its own storage |

Keys match `[a-z0-9][a-z0-9.-]{0,63}`, the part before the first dot is the game (`bedwars.wins` belongs to `bedwars`). Underscores are not allowed so placeholders can split on them. Top lists are never read per call: one read of all stats builds every list that was asked for in the last ten minutes, every `leaderboards.refresh-seconds`. With `MONGO` or `MYSQL` every server counts into the same numbers, with `LOCAL` or `SQLITE` each server has its own. Details: [`core-paper.md`](modules/core-paper.md#stats-and-leaderboards).

### Stats of other plugins

`/stats`, the PlaceholderAPI placeholders and the lobby leaderboards are open for every plugin. There are three ways in, and a plugin can mix them.

**1. Push values.** `increment`, `set` and `record` as above. Nothing else is needed: a key nobody described gets a name from the key (`bedwars.final-kills` shows as "Final kills" under the game "Bedwars", `bedwars.solo.wins` as "Solo wins") and a format guessed from its unit (`-millis` or `-ms` as `1:02.345`, `-seconds` as `1:02:03`, everything else as a grouped number in the viewer's language). `define` and `defineGame` make it look exactly right:

```java
prismarin.stats().ifPresent(stats -> {
    stats.define(new StatDefinition("bedwars.kd", NoticeText.of("K/D"), StatFormat.DECIMAL, StatOrder.HIGHEST,
            true, false));
    stats.defineGame(StatGame.of("bedwars", "Bed Wars").withName("de", "Bettenkrieg")
            .withIcon("RED_BED").withColor("#F87171").withOrder(10).withFeatured("wins", "final-kills"));
});
```

`StatGame`: `id` (the part before the dot, `[a-z0-9][a-z0-9-]{0,62}`), `name` (a `NoticeText`: one text, a message key or one text per language), `icon` (a material name such as `RED_BED`, for menus that list games), `color` (`#RRGGBB` or a colour name such as `gold`, used for the game in `/stats`), `order` (lower first, default `100`) and `featured` (stats shown first, with or without the game in front). Definitions and games registered through the API are also written to the shared collection `stat-catalog`, so a lobby without the game plugin names and formats its stats the same way (with `MONGO` or `MYSQL`; every server reads it on start and every five minutes). `stats.yml` still wins.

**2. Register a pull source.** A plugin with its own database answers on demand instead of pushing:

```java
Subscription source = prismarin.stats().orElseThrow().registerSource(
        StatSource.of("arena", player -> arenaDatabase.load(player)
                .thenApply(found -> Map.of("wins", found.wins(), "games", found.games()))));
```

| Method of `StatSource` | Default | Meaning |
| --- | --- | --- |
| `game()` | | the game the source answers for, one source per game (a new one replaces the old) |
| `values(player)` | | all time values as `CompletableFuture<Map<String, Long>>`; keys with or without the game in front (`wins` and `arena.wins` are the same) |
| `periods()`, `values(player, period)` | `ALL_TIME` only | a source that knows its weekly or monthly values lists those periods and answers them |
| `topLists()`, `top(key, period, size)` | `false`, empty | a source with its own top lists answers them; ranks are then only known for listed players |

The source is called off the main thread and may answer on any thread; a source that fails or takes longer than `sources.timeout-millis` (default 3 s) is skipped and the stored values show. Every read of a player's stats (`stats(player)`, `/stats`, `rank`, a join on any server with the source) asks the source and puts its values over the stored ones of that game; stored keys the source does not return stay.

Mirroring: when a source has no top lists of its own, every value it returns is written into the shared stats store when it is read, whenever it differs from the stored one. Leaderboards, ranks, the `top_` placeholders and the lobby boards then work from the store like for pushed stats, for every player the source was read for at least once (on join on a server with the plugin, or by `/stats`). Players nobody has looked at since the source was registered are missing until then. The first mirrored value of a key does not count into the running week and month; later changes move the weekly and monthly totals by the difference, unless the source answers those periods itself. Sources with their own top lists are never mirrored. The values of a source that is unregistered stay in the store.

**3. Read a PlaceholderAPI placeholder.** For plugins without any API, `stats.yml` maps a stat to a placeholder (`stats.bedwars.wins.placeholder: "%bedwars_stats_wins%"`), see [`core-paper.md`](modules/core-paper.md#placeholderapi-stats). Only servers where that placeholder resolves write the value.

## Daily rewards

```java
prismarin.dailyRewards().ifPresent(daily -> {
    daily.status(uuid).thenAccept(status -> log(status.streak() + " days, open " + status.claimable()
            + ", next reward " + daily.reward(status.nextStreak())));
    daily.claim(uuid).thenAccept(claim -> log(claim.result() + " day " + claim.day() + " of " + daily.cycle()));
});
prismarin.events().subscribe(DailyRewardClaimedEvent.class, event -> giveBonus(event.player(), event.claim()));
prismarin.events().subscribe(DailyStreakBrokenEvent.class, event -> log(event.lostStreak()));
```

`claim` needs the player online on this server and pays the reward of `daily-rewards.yml` (currencies, XP, levels, console commands, messages, custom rewards). The claim is one atomic update of the player's document: two servers that claim at the same moment get `CLAIMED` once and `ALREADY_CLAIMED` once. `DailyStatus` holds the streak, the best streak, the number of claims, whether today's reward is open, the next reset and the streak the next claim reaches.

## Votes

```java
prismarin.votes().ifPresent(votes -> {
    votes.vote("Steve", "my-own-list.com");
    votes.vote(uuid, "my-own-list.com");
    votes.stats(uuid).thenAccept(stats -> log(stats.total() + " votes, streak " + stats.streak()));
    votes.sites().thenAccept(sites -> sites.forEach(site -> log(site.name() + " " + site.url())));
});
prismarin.events().subscribe(VoteReceivedEvent.class, event -> log(event.vote().name()));
prismarin.events().subscribe(VoteRewardGivenEvent.class, event -> log(event.player()));
```

Votes live on the proxy. NuVotifier on the proxy delivers them, `vote(name, service)` and `vote(uuid, service)` count a vote from a site without Votifier. Every vote is queued with its reward in the proxy's storage; the first PRISMARIN backend with `modules.vote-rewards` the player is on takes it out of the queue (`claim`) and hands it out, so offline players get it on their next join on any server. `pending(uuid)` lists the queue, `claim(uuid, name[, limit])` takes votes out for plugins that give their own rewards. `VoteReceivedEvent` fires on the proxy and on every backend with players, `VoteRewardGivenEvent` on the backend that handed it out. Details: [`core-velocity.md`](modules/core-velocity.md#votes).

## Custom rewards

```java
Subscription crate = RewardApi.handlers().register("crate", (context, value) ->
        crates.give(context.player(), value, context.source()));
```

A reward in `daily-rewards.yml` or `votes.yml` with `custom: {crate: vote}` calls the handler on the main thread of the server that hands it out. `RewardContext` holds the source (`daily` or `vote`), the player, the name, the streak and the detail (day of the cycle or vote service). Unknown ids are logged and skipped.

PRISMARIN registers the type `cosmetic` itself while `modules.cosmetics` is on: `custom: {cosmetic: angel-wings}` unlocks that cosmetic for the player (source `REWARD`) and tells him, a player who owns it already reads `cosmetics.unlocked-owned`. The default `daily-rewards.yml` gives the reward only cosmetic `streak-flame` at the 30 day milestone.

## Cosmetics

```java
prismarin.cosmetics().ifPresent(cosmetics -> {
    cosmetics.purchase(uuid, "angel-wings").thenAccept(result -> log(result.status() + ", missing " + result.missing()));
    cosmetics.unlock(uuid, "streak-flame", CosmeticSource.REWARD);
    cosmetics.equip(uuid, "angel-wings");
    cosmetics.unequip(uuid, CosmeticSlot.HAT);
    cosmetics.equipped(uuid, CosmeticSlot.AURA).ifPresent(aura -> log(aura.id()));

    cosmetics.enable(CosmeticSlot.TRAIL);
    cosmetics.setAutomatic(CosmeticSlot.KILL_EFFECT, false);
    cosmetics.playEffect(killer, CosmeticSlot.KILL_EFFECT, CosmeticSpot.of(world, x, y, z, yaw));
    cosmetics.playEffect(winner, CosmeticSlot.WIN_EFFECT);
    cosmetics.cosmetic("fireworks-show").ifPresent(show -> cosmetics.playEffect(winner, show, podium));
    cosmetics.trail(shooter, fireball.getUniqueId());
    boolean skip = cosmetics.effectEntity(entity.getUniqueId());
    cosmetics.stopEffects(winner);
    cosmetics.resetAutomatic(CosmeticSlot.KILL_EFFECT);
    cosmetics.reset(CosmeticSlot.TRAIL);
});
prismarin.events().subscribe(CosmeticPurchaseEvent.class, event -> event.setCancelled(roundRunning));
prismarin.events().subscribe(CosmeticEffectEvent.class, event -> event.setCancelled(
        event.trigger() == CosmeticEffectEvent.Trigger.KILL && !finalKill(event.player())));
```

| Call | What it does |
|:---|:---|
| `cosmetics()`, `cosmetic(id)`, `cosmetics(slot)` | the catalogue: `cosmetics.yml` plus cosmetics of plugins, sorted like the menu |
| `slots()`, `rarities()`, `rarity(id)` | categories that are switched on and have cosmetics, in menu order; rarities with colour and order |
| `load(player)`, `cached(player)` | `PlayerCosmetics(player, owned, equipped, hidesOthers)` from the storage, the cache holds players on this server |
| `owns(player, id)`, `equipped(player, slot)` | owned in the storage or through a permission (`permission` of the cosmetic or `prismarin.cosmetics.all`), the worn cosmetic of a slot |
| `purchase(player, id)` | checks ownership, fires `CosmeticPurchaseEvent`, withdraws the price, stores the cosmetic and pays back when storing fails or another server was faster |
| `unlock(player, id[, source])`, `revoke(player, id)` | give or take away without money; revoking takes it off too |
| `equip(player, id)`, `unequip(player, slot)` | one cosmetic per slot, the old one comes off; fires `CosmeticEquipEvent` before and `CosmeticUnequippedEvent` after |
| `hideOthers(player, hide)` | the viewer setting: no particles of other players |
| `register(cosmetic)`, `registerShape(id, renderer)`, `addViewFilter(filter)` | extension points, see below |
| `active(slot)`, `enable(slot)`, `disable(slot)`, `reset(slot)` | whether a category shows on this server; game plugins switch it for a round, `reset` goes back to `cosmetics.yml` |
| `playEffect(player, slot[, spot])` | plays the player's cosmetic of that slot at the spot for everyone who may see it; without a spot it plays where he stands and a running effect follows him; `false` when he wears none there, the category is not active here, he is vanished or spectating, too many effects run or a `CosmeticEffectEvent` was cancelled |
| `playEffect(player, cosmetic, spot)` | plays a given cosmetic (also one that is not registered) as if the player wore it, for example a default effect for winners who wear none; `spot` `null` follows him; its category must be enabled |
| `stopEffects(player)` | ends the running effects of a player and removes their entities, the number of effects |
| `trail(player, projectile)` | starts the player's projectile trail on a projectile a game spawned itself, regardless of the automatic trigger |
| `automatic(slot)`, `setAutomatic(slot, on)`, `resetAutomatic(slot)` | the automatic triggers of `PROJECTILE_TRAIL` (projectiles shot or thrown by players) and `KILL_EFFECT` (player kills) on this server; `reset` goes back to `triggers` in `cosmetics.yml`, other slots throw `IllegalArgumentException` |
| `effectEntity(uuid)`, `CosmeticApi.effectEntity(uuid)` | `true` for squids, sheep, items, fireworks, displays and lightning of cosmetic effects; game plugins that count mobs or entities skip them |
| `registerEffectKind(kind, effect)` | an effect kind of your own, see below |

Every call that changes something returns `CosmeticResult(status, cosmetic, missing)` with `DONE`, `UNKNOWN`, `CATEGORY_OFF`, `NOT_OWNED`, `ALREADY_OWNED`, `ALREADY_EQUIPPED`, `NOT_EQUIPPED`, `NOT_FOR_SALE` (no price, unknown currency or economy off), `NOT_ENOUGH_MONEY` (`missing` is the difference in the cosmetic's currency), `CANCELLED` or `FAILED`. Ownership, the worn cosmetics and the viewer setting are one document `cosmetics/<uuid>` that every change updates atomically, so with `MONGO`, `MYSQL` or `MARIADB` they count on every server. Built in slots (`CosmeticSlot`): `TRAIL` (particles while walking), `AURA` (particles while standing, while walking too when no trail is worn), `HAT` (head slot), and the effect slots `PROJECTILE_TRAIL` (follows the projectiles a player shoots or throws, by itself), `KILL_EFFECT` (plays where his victim fell, by itself) and `WIN_EFFECT` (through `playEffect` or `/cosmetics play`, because Bukkit has no win event). A game that wants to decide itself, for example only final kills, switches the automatic trigger off with `setAutomatic(slot, false)` or `triggers.<slot>.auto: false` and plays the effect through the API; a game without code adds `cosmetics play {player} win-effect` to the commands it runs for winners (see [`core-paper.md`](modules/core-paper.md#win-effects-without-code)).

### Own cosmetics, shapes and filters

```java
Subscription star = cosmetics.registerShape("example-star", frame -> List.of(
        CosmeticPoint.of(frame.rightX() * 0.5, 1.2, frame.rightZ() * 0.5),
        new CosmeticPoint(0, 2.2, 0, 0xFDE68A)));
Subscription cosmetic = cosmetics.register(Cosmetic.particles("example-star", CosmeticSlot.AURA, "Star",
                CosmeticParticles.of("example-star", "dust").withColors(0xFDE68A, 0xF472B6))
        .withPrice("coins", 1500).withRarity("epic").withIcon("nether_star"));
Subscription teams = cosmetics.addViewFilter((viewer, wearer, slot) -> !slot.equals(CosmeticSlot.AURA)
        || sameTeam(viewer, wearer));
```

- A `Cosmetic` has an id (`[a-z0-9][a-z0-9-]{0,47}`), a slot, a name and a description (`NoticeText`), a rarity, an icon, a `CosmeticPrice(currency, amount)` or none, a permission, `hidden` and `order`, and its look: `CosmeticParticles` for every slot but the hat, `CosmeticHat(material, texture)` for hats. `cosmetics.yml` wins: an id it already has is refused. A slot of your own (`CosmeticSlot.of("pet")`) gets its own menu category and shows through `playEffect`.
- `CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points, spin, sound, kind, duration, variant)` (the constructor without the last three still works): built in shapes are `point`, `ring`, `halo`, `spiral`, `orbit`, `wings`, `burst`, `heart`, `pillar`, `wave`, `rings`, `meteor`, `rain`, `swirl`, `ghost`, `crown`, `gyro`, `rain-cloud` and `none`; `radius`, `points` and `spin` 0 use the shape's own value, `height` lifts it. `kind` (default `particles`) picks the effect kind, `duration` (0 to 200 ticks) lets an effect run, `variant` is the block or item of block and item particles, fountains and tombstones or the firework type. `withKind`, `withDuration`, `withVariant` and `runs()` help.
- A `CosmeticRenderer` gets a `CosmeticFrame(player, tick, yaw, moving, look)` and returns `CosmeticPoint(x, y, z, color)` offsets from the feet of the wearer or from the spot of an effect, in world axes. `forwardX/Z` and `rightX/Z` turn the frame's yaw into directions. A colour of `-1` takes the look's palette by index. At most 200 points per frame, a failing renderer is logged once and draws nothing. Shapes `cosmetics.yml` names but nobody registered draw a point and are logged once.
- A `CosmeticViewFilter` can only hide more: it runs after vanish, the lobby player hider, the silent lobby and the viewer's own setting, never for the wearer himself.

### Own effect kinds

```java
Subscription bubbles = cosmetics.registerEffectKind("example-bubbles", new CosmeticEffectKind() {
    @Override
    public void frame(CosmeticEffectFrame frame) {
        if (frame.tick() % 4 == 0) {
            frame.draw(List.of(CosmeticPoint.of(0, 0.5 + frame.tick() * 0.05, 0)));
        }
        if (frame.first()) {
            frame.sound("block.bubble_column.upwards_inside", 1.0f, 1.2f);
        }
    }

    @Override
    public void end(CosmeticEffectFrame frame) {
        frame.drawShape();
    }
});
```

`cosmetics.yml` names the kind with `effect.kind: example-bubbles` and `effect.duration`. The effect scheduler calls `frame` once per tick on the main thread, from tick 0 to `duration`, and `end` once at the end, also when the effect is stopped early. The frame knows `player()`, `cosmetic()`, `look()`, `spot()` (follows the player for effects without a spot), `tick()`, `duration()`, `first()`, `last()`, `preview()` (only the viewer in the menu sees it) and `viewers()`. `drawShape()` draws the look's shape for this tick, `draw(points)` and `draw(look, points)` draw points around the spot; both share the per tick particle budget and return `false` when this tick is full. `sound(key, volume, pitch)` plays to the viewers only. An entity you spawn for an effect should be spawned with `setVisibleByDefault(false)`; `adopt(uuid)` then shows it to the viewers, marks it as an effect entity (`effectEntity` is `true`, PRISMARIN cancels damage, pickups and interaction for it) and removes it when the effect ends. A kind that throws is logged once and its effect stopped. Built in kinds (`particles`, `lightning`, `lightning-storm`, `squid`, `fountain`, `tombstone`, `firework`, `fireworks`, `totem`, `sheep`) cannot be replaced; unknown kinds draw particles and are logged once.

### Events

| Event | When | Thread |
|:---|:---|:---|
| `CosmeticPurchaseEvent` (cancellable) | before the money is taken | main thread |
| `CosmeticEquipEvent` (cancellable, `previous()`) | before a cosmetic is put on | main thread |
| `CosmeticUnlockedEvent(player, cosmetic, source)` | after it is stored, for purchases, rewards, `/cosmetics give` and `unlock` | the storage thread |
| `CosmeticUnequippedEvent(player, cosmetic, slot, reason)` | `UNEQUIPPED`, `REPLACED` by another one, `REVOKED` | the storage thread |
| `CosmeticEffectEvent(player, cosmetic, spot, trigger)` (cancellable) | before an effect plays or a projectile trail starts; `trigger()` is `KILL`, `PROJECTILE`, `COMMAND` or `API`, not fired for previews | main thread |

Details of the module, the menu and `cosmetics.yml`: [`core-paper.md`](modules/core-paper.md#cosmetics).

## Friends and parties

```java
prismarin.parties().flatMap(parties -> parties.partyOf(uuid)).ifPresent(party -> teams.join(party.memberIds()));
prismarin.players().areFriends(first, second).thenAccept(together -> { });
prismarin.friends().ifPresent(friends -> friends.sendRequest(sender, target));
```

On a backend the party mirror answers without waiting; actions go to the proxy as the acting player. Clans have their own section below. Details: [`core-paper.md`](modules/core-paper.md#friends-and-parties-for-backend-plugins) and [`core-velocity.md`](modules/core-velocity.md).

## Clans

`prismarin.clans()` returns `Optional<ClanService>` from `net.prismarin.api.social`, on Paper and on Velocity alike; on the proxy it is empty without the `clans` module; on Paper the service is always registered, with clans off on the proxy lookups come back empty and actions fail. `players().clan(uuid)` and `players().lookupClan(uuid)` are shortcuts.

Read model: `Clan` (`id`, `name`, `tag`, `color`, `access`, `leader`, `members`, `invited`, `data`, `created`) with `member`, `role`, `withRole`, `contains`, `data(key)`, `number(key, fallback)` and `can(actor, ClanAction, target, rights)`; `ClanMember` (`uuid`, `name`, `role`, `joined`); `ClanRole` (`MEMBER`, `MODERATOR`, `CO_LEADER`, `LEADER`, with `atLeast`, `outranks`, `above`, `below`); `ClanRights` (lowest role per `ClanAction`, from `social.yml`); `ClanRules`; `ClanPage`; `ClanResult` (`SocialResult` plus the clan after the change).

| Group | Methods |
| --- | --- |
| Lookups | `clanOf(player)`, `clan(id)` without waiting (on Paper only for clans with a member on this server), `lookup(player)`, `byId(id)`, `find(nameOrTag)`, `list(page, pageSize)` (biggest first, at most 50 per page on Paper), `rights()`, `formatTag(clan)`, `formattedTag(player)` |
| As a player | `create`, `invite`, `join`, `leave`, `kick`, `promote`, `demote`, `transfer`, `disband`, `changeColor`, `changeAccess`, `chat`: same checks and chat feedback as the commands, the player has to be online |
| Management | `createClan(leader, name, tag)`, `renameClan(id, name, tag)` (`null` keeps a part), `disbandClan(id)`, `addMember(id, player, role)`, `removeMember(id, player)`, `setRole(id, player, role)` (`LEADER` hands over the lead), `setColor(id, color)`, `setAccess(id, access)`, `setData(id, key, value)`, `broadcast(id, message)`: no role checks, players may be offline; names, tags and colours are still validated |

Clan data is a small string map on the clan, stored with it and sent to every server: keys match `[a-z0-9_.:-]{1,48}` (prefix them with your plugin, `wars:points`), values have at most 256 characters, a clan holds at most 32 keys; `null` or an empty value removes a key. For bigger data use `prismarin.storage()` with the clan id as document id.

### Example: a small clan war

```java
ClanService clans = prismarin.clans().orElseThrow();

void startWar(UUID attackerId, UUID defenderId) {
    clans.byId(attackerId).thenCombine(clans.byId(defenderId), (attacker, defender) -> {
        if (attacker.isEmpty() || defender.isEmpty()) {
            return null;
        }
        String line = "&6War: &f" + attacker.get().name() + " &7against &f" + defender.get().name();
        clans.broadcast(attackerId, line);
        clans.broadcast(defenderId, line);
        return null;
    });
}

void kill(UUID killer) {
    clans.clanOf(killer).ifPresent(clan -> clans.setData(clan.id(), "wars:points",
            String.valueOf(clan.number("wars:points", 0) + 1)));
}

prismarin.events().subscribe(ClanDataChangedEvent.class, event -> {
    if ("wars:points".equals(event.key()) && event.clan().number("wars:points", 0) >= 50) {
        clans.setData(event.clan().id(), "wars:wins", String.valueOf(event.clan().number("wars:wins", 0) + 1));
        clans.setData(event.clan().id(), "wars:points", null);
    }
});
prismarin.events().subscribe(ClanJoinEvent.class, event -> {
    if (warRunning(event.clan().id())) {
        event.setCancelled(true);
    }
});
```

The player sees `%prismarin_clan_data_wars:points%` on a scoreboard. Writes are last write wins per key; count on one server (the game server of the war) to avoid lost updates.

### Events

Post events (records) fire on the proxy and are relayed to every backend with players: `ClanCreatedEvent`, `ClanDisbandedEvent`, `ClanRenamedEvent`, `ClanMemberJoinedEvent`, `ClanMemberLeftEvent` (reason `LEFT`, `KICKED`, `REMOVED`), `ClanRoleChangedEvent` (also for hand overs), `ClanColorChangedEvent`, `ClanAccessChangedEvent`, `ClanDataChangedEvent`, `ClanInviteEvent`. Cancellable events implement `net.prismarin.api.event.Cancellable` and fire on the proxy before the change: `ClanCreateEvent`, `ClanJoinEvent` (invite, open join and `addMember`), `ClanDisbandEvent` (also when the last member leaves), `ClanChatEvent` (before the message goes out; backends get it afterwards, cancelling there changes nothing). A cancelled action answers `CANCELLED`.

## JoinMe

`prismarin.joinMe()` returns `Optional<JoinMeService>` from `net.prismarin.api.joinme`. The proxy keeps every JoinMe; a game server forwards to it, adds the game side and runs the handlers. Details: [`core-velocity.md`](modules/core-velocity.md#joinme) and [`core-paper.md`](modules/core-paper.md#joinme).

| Method | What it does |
| --- | --- |
| `preview(uuid)`, `preview(JoinMeRequest)` | builds the same message as `create` (handler, placeholders, face rows, skin face with nick skin) and sends it only to the creator; no cooldown, no cost, no group or game state check, nothing is stored and no create event fires. `SUCCESS`, `OFFLINE`, `DISABLED`, `UNAVAILABLE` or `FAILED` |
| `create(uuid)`, `create(JoinMeRequest)` | starts a JoinMe for an online player with the same checks as `/joinme` (cooldown, groups, game state, vanish, cost on a game server). `JoinMeRequest.of(uuid)` with `withLifetime`, `withMaxJoins`, `withAudience`, `withPlaceholder(s)`, `ignoringCooldown()`, `withoutCost()` |
| `active()`, `visibleTo(viewer)`, `find(id)`, `byCreator(uuid)` | open JoinMes, `visibleTo` applies the friends, party and clan filter |
| `join(player, id)` | the same as a click, the player is connected and placed |
| `cancel(id)`, `cancelFor(creator)` | ends it with `CANCELLED` |
| `registerHandler(handler)`, `registerHandler(JoinMeScope, handler)`, `handlers()` | game server only; on the proxy `registerHandler` throws |

Every call answers a `JoinMeResult(status, joinMe, waitMillis)`; `status` is one of `SUCCESS`, `DISABLED`, `NO_PERMISSION`, `OFFLINE`, `COOLDOWN` (with `waitMillis`), `GROUP_BLOCKED`, `GAME_STATE`, `VANISHED`, `REFUSED`, `NOTHING_TO_JOIN`, `NOT_ENOUGH_MONEY`, `CANCELLED`, `NOT_FOUND`, `EXPIRED`, `USED_UP`, `OWN`, `ALREADY_THERE`, `OTHER_SERVER`, `SERVER_OFFLINE`, `UNAVAILABLE`, `FAILED`. API calls send no chat feedback, the plugin decides. Read model `JoinMe`: `id`, `creator`, `creatorName`, `shownName` (the nick at creation), `name(revealed)`, `server`, `group`, `world`, `state`, `handler`, `placeholders`, `audience` (`JoinMeAudience`: scope `NETWORK` or `GROUPS`, `groups`, `circles` `FRIENDS`, `PARTY`, `CLAN`), `maxJoins`, `joins`, `joinsLeft()`, `createdAt`, `expiresAt`, `open(now)`.

### A game plugin decides what joining means

A handler runs on the game server. It can refuse a JoinMe, add placeholders and face rows for the message, and place every player who arrives through a click.

```java
public final class ArenaJoinMe implements JoinMeHandler {

    private final Arenas arenas;

    public ArenaJoinMe(Arenas arenas) {
        this.arenas = arenas;
    }

    @Override
    public String id() {
        return "bedwars";
    }

    @Override
    public boolean allows(JoinMeSpot spot) {
        return arenas.of(spot.creator()).map(Arena::waiting).orElse(false);
    }

    @Override
    public Map<String, String> placeholders(JoinMeSpot spot) {
        return arenas.of(spot.creator())
                .map(arena -> Map.of("map", arena.map(), "mode", arena.mode()))
                .orElse(Map.of());
    }

    @Override
    public Map<Integer, String> lines(JoinMeSpot spot) {
        return Map.of(4, "<gray>on</gray> <aqua>%map%</aqua> <dark_gray>·</dark_gray> <gold>%mode%</gold>");
    }

    @Override
    public JoinMeOutcome arrive(JoinMeArrival arrival) {
        Player player = Bukkit.getPlayer(arrival.player());
        Optional<Arena> arena = arenas.of(arrival.creator());
        if (player == null || arena.isEmpty()) {
            return JoinMeOutcome.FALLBACK;
        }
        if (arena.get().full()) {
            return JoinMeOutcome.REFUSED;
        }
        arena.get().join(player, arena.get().teamOf(arrival.creator()));
        return JoinMeOutcome.HANDLED;
    }
}

Subscription handler = prismarin.joinMe().orElseThrow().registerHandler(
        JoinMeScope.when(spot -> arenas.of(spot.creator()).isPresent()), new ArenaJoinMe(arenas));
```

- `allows`, `placeholders` and `lines` run on the main thread when the creator types `/joinme`, `arrive` runs on the main thread `arrival.delay-ticks` after the player joined the server (or right away when he already was there). `arrival.party()` is true for party members who followed their leader.
- `FALLBACK` hands the player to the configured fallback (same world, spawn or next to the creator), `REFUSED` tells him and sends him back.
- Without a handler JoinMe puts the player on the creator's server (`join.same-server` on the proxy) and into the creator's world (`fallback.same-world` on the game server); both together are the default for multi world servers.
- Unregister in `onDisable` with `handler.unsubscribe()`.

### Events

| Event | Fires | Notes |
| --- | --- | --- |
| `JoinMeCreateEvent` | on the creator's game server before the request goes to the proxy, then on the proxy before the message goes out | cancellable; `placeholders()`, `placeholder(key, value)`, `lines()`, `line(row, text)`, `lifetime(duration)`, `maxJoins(count)`, `audience(audience)` |
| `JoinMeCreatedEvent(joinMe)` | the proxy, relayed to every game server with players | after the message went out |
| `JoinMeJoinEvent` | on the game server of the clicking player, then on the proxy before the connect | cancellable |
| `JoinMeArrivedEvent(player, joinMe, placement, handler, party)` | the target game server | after the handler or the fallback |
| `JoinMeExpiredEvent(joinMe, reason)` | the proxy, relayed to game servers | `EXPIRED`, `USED_UP`, `CANCELLED`, `CREATOR_LEFT`, `REPLACED` |
| `JoinMePreviewEvent` | on the creator's game server for `/joinme preview`, then on the proxy | not cancellable; `placeholders()`, `placeholder(key, value)`, `lines()`, `line(row, text)`, so a plugin that adds rows in `JoinMeCreateEvent` can add them to the preview too |

```java
prismarin.events().subscribe(JoinMeCreateEvent.class, event -> {
    if (tournament.running()) {
        event.setCancelled(true);
        return;
    }
    event.placeholder("season", "3");
    event.line(9, "<gray>Season</gray> <gold>%season%</gold>");
});
```

## Skin faces in chat

`prismarin.skinFaces()` (`SkinFaces` in `net.prismarin.api.skin`) turns a skin into a `ChatFace`: 64 RGB pixels of the face with the hat laid over it. `face.lines()` gives 8 `Component`s of `█` in the exact colours, `lines(symbol)` another character, `line(row, symbol)`, `rgb(x, y)` and `color(x, y)` single pixels. `fallback()` is true for the built-in Steve and Alex faces.

```java
SkinFaces faces = prismarin.skinFaces().orElseThrow();
faces.face(player.getUniqueId()).thenAccept(face -> {
    List<Component> lines = face.lines();
    for (int row = 0; row < lines.size(); row++) {
        player.sendMessage(lines.get(row).append(Component.text(row == 3 ? "  Welcome back" : "")));
    }
});
faces.fromTextures(texturesPropertyValue, owner).thenAccept(face -> board.show(face.lines("■")));
```

`face(uuid)` reads the skin other players see: on a game server the player's current profile (a nick's skin included), on the proxy the login profile and the default face for nicked players. `fromTextures(value, owner)` takes the base64 value of a `textures` profile property, `defaultFace(owner)` the built-in face, `cached(value)` answers without waiting. Downloads only go to `https://textures.minecraft.net/texture/<hash>`, run off the main thread and are cached by texture in memory and optionally on disk (`face.*` in `joinme.yml`).

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

## Chat filter

```java
prismarin.chatFilter().ifPresent(filter -> {
    ChatFilterResult sign = filter.check(String.join(" ", lines));
    if (sign.blocked()) {
        event.setCancelled(true);
    }

    ChatFilterResult clan = filter.filter(new ChatMessage(uuid, name, "clan", text));
    if (!clan.blocked()) {
        clanChat.broadcast(clan.text());
    }

    filter.registerRule(new ChatFilterRule() {
        @Override
        public String id() {
            return "no-spoilers";
        }

        @Override
        public Optional<ChatFilterMatch> check(ChatMessage message) {
            return message.text().contains("the ending")
                    ? Optional.of(ChatFilterMatch.replace(id(), "spoiler", message.text().replace("the ending", "***")))
                    : Optional.empty();
        }
    });
});

prismarin.events().subscribe(ChatFilteredEvent.class, event -> {
    if (event.message().channel().equals("clan") && event.result().rules().equals(List.of("caps"))) {
        event.setCancelled(true);
    }
});
```

| Call | What it does |
|:---|:---|
| `check(text)` | runs the content rules (`words`, `links`, `caps` and plugin rules) without player state and without side effects. For signs, books, item names, clan tags |
| `filter(message)` | everything PRISMARIN does with its own chat: every rule including `flood` and `repeat` (counted per player and channel), `ChatFilteredEvent`, the text for the writer, the staff alert, the audit log and the escalation on the proxy. Use it only for messages PRISMARIN does not filter itself, otherwise flood and repeat count twice |
| `rules()` | ids of the active rules: `flood`, `repeat`, `words`, `links`, `caps`, then plugin rules |
| `registerRule(rule)`, `unregisterRule(id)` | plugin rules run after the built-in ones and see the text after their replacements. Ids match `[a-z0-9][a-z0-9_-]{0,31}`, built-in ids are refused, a second rule with the same id replaces the first. Operators tune them in `chat-filter.yml` under `custom.<id>` (enabled, action, staff alert, audit, channels, escalation) |

| Type | What it holds |
|:---|:---|
| `ChatMessage` | sender (or `NOBODY`), sender name, channel (`public`, `private`, `party`, `text` or a name of your own) and text |
| `ChatFilterMatch` | rule id, `ChatFilterAction`, detail (the word, domain or percentage that matched) and, for `REPLACE`, the changed text. Factories `block`, `warn`, `log`, `replace` |
| `ChatFilterAction` | `BLOCK` (not sent), `REPLACE` (sent changed), `WARN` (sent, the writer is warned), `LOG` (sent, only staff and the audit log know) |
| `ChatFilterResult` | original and shown text, `blocked`, every match; `clean()`, `changed()`, `warned()`, `blocking()`, `rules()` |

The filter runs where the message is: a backend filters its public chat (`AsyncChatEvent` at priority `LOW`, so it also works when another plugin formats the chat), the proxy filters the commands listed under `proxy-commands` in its own `chat-filter.yml` (private and party messages). The backend reports every hit that needs the proxy as `chat-violation` on `prismarin:moderation`; the proxy shows it to `prismarin.staff.chatfilter`, writes the audit log and counts hits for the whole network. After `escalation.violations` hits of one rule within `escalation.within` the proxy applies the punishment template `escalation.template` with the next step of the player's record (actor "Chat filter") and, with `escalation.report: true`, files a report whose chat evidence is exactly the messages that counted. `ChatFilterEscalatedEvent` follows on the proxy.

`ChatFilteredEvent` fires on the server that filtered the message, after the rules and before the writer is told. Cancelling it lets the original message through and skips the alert, the audit entry and the escalation. Details and every setting: [`core-paper.md`](modules/core-paper.md#chat-filter) and [`core-velocity.md`](modules/core-velocity.md#chat-filter-on-the-proxy).

## Alt accounts

```java
prismarin.alts().ifPresent(alts -> alts.bannedAlts(uuid).thenAccept(banned -> {
    if (!banned.isEmpty()) {
        log(name + " shares an address with the banned account " + banned.getFirst().name());
    }
}));
prismarin.events().subscribe(BannedAltJoinEvent.class, event ->
        log(event.name() + " joined with " + event.bannedAlts().size() + " banned alts, denied " + event.denied()));
```

| Call | Result |
|:---|:---|
| `alts(uuid)` | every account that used one of the player's addresses within the retention, newest first, as `AltAccount` (uuid, name, first and last seen, number of shared addresses, `banned`, `muted`) |
| `bannedAlts(uuid)` | only the accounts with a ban in force |
| `related(first, second)` | whether `second` is one of the alts of `first` |

The proxy stores a salted SHA-256 hash of every login address (IPv6 as its /64 network) with the salt of the IP bans, never the address itself, and neither the address nor the hash ever leaves the proxy through the API. Addresses that more accounts share than `shared-address-limit` (schools, mobile networks, a proxy in front of the network) link nobody. On a backend every call goes to the proxy and fails with "alt detection is off on the proxy" when `modules.alts` is off there. Details: [`core-velocity.md`](modules/core-velocity.md#alt-accounts).

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

## Reload and updates

`/prismarin reload` on a backend and `/prismarinproxy reload` on the proxy (permission `prismarin.admin.reload`) reload every registered part and fire `PrismarinReloadedEvent(result, actor)`. PRISMARIN registers its languages and, on Paper, the rank display. Another plugin adds its own config:

```java
Subscription part = prismarin.reloads().register("bedwars", this::loadConfig);
prismarin.events().subscribe(PrismarinReloadedEvent.class, event -> getLogger().info(event.result().reloaded().toString()));
```

A failing part is reported in `ReloadResult#failed` and does not stop the others. Storage, Redis and module switches still need a restart.

`prismarin.updates()` checks GitHub for a newer release (`update-check` in `config.yml`, every 6 hours by default) and fires `UpdateAvailableEvent` once per new version. `available()` returns the pending update, staff with `prismarin.admin.update` get a clickable note on join.

## Game state, servers and connecting

```java
prismarin.gameState().state(GameState.WAITING, "3/8");
prismarin.gameState().state(GameState.RUNNING);
prismarin.servers().byGroup("bedwars").forEach(server -> log(server.name()));
prismarin.connector().connectToGroup(uuid, "bedwars");
prismarin.serverStatus().status("bw-1").ifPresent(status -> log(status.players() + "/" + status.maxPlayers()));
```

Lobbies see the game state through the server list ping even without Redis. Details: [`core-paper.md`](modules/core-paper.md).

With the server queue on, `connect` to a full server puts the player into the queue of that server, and `connectToGroup` joins the group queue; it returns the server only when the player was sent right away and an empty `Optional` while he waits.

## Server queue

```java
prismarin.queue().ifPresent(queue -> queue.join(uuid, QueueTarget.group("bedwars")).thenAccept(result -> {
    if (result.sent()) {
        log("off to " + result.server().orElseThrow());
    } else if (result.waiting()) {
        log("place " + result.position().orElseThrow().position() + " of " + result.position().orElseThrow().size());
    }
}));

prismarin.queue().ifPresent(queue -> queue.joinable(prismarin.serverId(), false));
prismarin.gameState().state(GameState.RUNNING);

prismarin.events().subscribe(QueueSentEvent.class, event ->
        log(event.player() + " waited " + event.waitedMillis() + " ms for " + event.target().id()));
```

The queue runs on the proxy, one per server and one per server group of `servers.yml`. A player who connects to a full server (or one whose round runs) waits there and is sent as soon as there is room, highest priority first and first come, first served within a priority. A party leader queues the whole party as one unit that is only sent when everyone fits. On Paper every call goes to the proxy; with `modules.queue` off there, `join` answers `DISABLED` and the rest fails with the proxy's reason.

| Call | What it does |
|:---|:---|
| `join(player, target)` | gets the player onto a server or group: `SENT` (with `server`) when there is room, `QUEUED` or `ALREADY_QUEUED` (with `position`) when he waits. `ALREADY_THERE`, `NOT_LEADER` (a party member while `members-queue-alone` is false), `UNKNOWN_TARGET`, `NO_SERVER` (nothing online), `OFFLINE`, `CANCELLED` (a plugin cancelled `QueueJoinEvent`), `DISABLED`, `FAILED`. The proxy tells the player about waiting, the caller about everything else. Bypass and switched off targets connect directly |
| `leave(player)`, `leave(player, target)` | leaves every queue or one, `true` when the player was in it |
| `positions(player)`, `position(player)` | `QueuePosition`: target, owner of the unit, party, place, size, players, weight, paused, joined, online |
| `size(target)`, `queues()`, `queue(target)` | `QueueInfo` with display name, units, players, paused and, for one queue, its `QueueEntry`s (on Paper cut to the message size) |
| `pause(target, actor)`, `resume(target, actor)` | holds a queue, players keep their places; works on empty queues too |
| `clear(target)` | removes everyone, returns how many units |
| `sendNext(target)` | sends the next online unit even if paused or full, returns its owner |
| `joinable(server, joinable)`, `closedServers()` | a plugin closes its server for the queue (for example during a round) and opens it again |

`QueueTarget.group("bedwars")`, `QueueTarget.server("bw-1")` and `QueueTarget.parse("group:bedwars")` name targets, names are lowercased. `QueueApi.BYPASS_PERMISSION` is `prismarin.queue.bypass`. Settings, commands and permissions: [`core-velocity.md`](modules/core-velocity.md#server-queue), backends: [`core-paper.md`](modules/core-paper.md#server-queue-on-backends).

The queue is per proxy: with several proxies each one keeps its own queues and shares only the player counts of the servers through the ping.

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
| `ChatFilteredEvent` | `moderation` | the server that filtered the message: the backend for public chat, the proxy for private and party messages; cancellable |
| `ChatFilterEscalatedEvent`, `BannedAltJoinEvent` | `moderation` | the proxy |
| `FriendRequestEvent`, `FriendAddedEvent`, `FriendRemovedEvent` | `social` | the proxy |
| `ClanCreatedEvent`, `ClanDisbandedEvent`, `ClanRenamedEvent`, `ClanMemberJoinedEvent`, `ClanMemberLeftEvent`, `ClanRoleChangedEvent`, `ClanColorChangedEvent`, `ClanAccessChangedEvent`, `ClanDataChangedEvent`, `ClanInviteEvent` | `social` | the proxy, relayed to every backend with players |
| `ClanCreateEvent`, `ClanJoinEvent`, `ClanDisbandEvent`, `ClanChatEvent` (cancellable) | `social` | the proxy before the change; `ClanChatEvent` also reaches backends after delivery |
| `PartyCreatedEvent`, `PartyInviteEvent`, `PartyJoinedEvent`, `PartyLeftEvent`, `PartyRoleChangedEvent`, `PartySettingsChangedEvent`, `PartyDisbandedEvent` | `social` | the proxy, and backends with a member or invitee online |
| `QueueJoinedEvent`, `QueueLeftEvent` (reason `LEFT`, `SWITCHED_SERVER`, `DISCONNECTED`, `OTHER_QUEUE`, `SENT_ELSEWHERE`, `PARTY_CHANGED`, `CLEARED`, `FAILED`), `QueueSentEvent`, `QueuePausedEvent` | `queue` | the proxy, relayed to every backend with players |
| `QueueJoinEvent`, `QueueSendEvent` (cancellable) | `queue` | the proxy before a player is put into a queue (also when he is sent right away) and before a unit is sent |
| `MaintenanceChangedEvent` | `admin` | the proxy |
| `StaffAlertEvent` | `admin` | the proxy |
| `StatChangedEvent`, `StatRecordEvent` | `stats` | the server that changed the stat |
| `DailyRewardClaimedEvent`, `DailyStreakBrokenEvent` | `reward` | the backend that paid the reward |
| `VoteReceivedEvent` | `reward` | the proxy, relayed to backends with players |
| `VoteRewardGivenEvent` | `reward` | the backend that handed the reward out |
| `JoinMeCreatedEvent`, `JoinMeExpiredEvent` | `joinme` | the proxy, relayed to backends with players |
| `JoinMeCreateEvent`, `JoinMeJoinEvent` (cancellable) | `joinme` | the backend of the player, then the proxy |
| `JoinMeArrivedEvent` | `joinme` | the backend the player arrived on |
| `JoinMePreviewEvent` | `joinme` | the backend of the creator, then the proxy |
| `CosmeticPurchaseEvent`, `CosmeticEquipEvent`, `CosmeticEffectEvent` (cancellable) | `cosmetics` | the backend, main thread, before the change or the effect |
| `CosmeticUnlockedEvent`, `CosmeticUnequippedEvent` | `cosmetics` | the backend that stored the change |

## The example plugin

`example-paper` is built with the project but not part of `dist`. It depends only on the API and Paper:

| Class | Shows |
|:---|:---|
| `ExamplePlugin` | getting `Prismarin`, wiring, cleaning up subscriptions |
| `PlayerLookup` | `resolve`, `displayName`, `rank`, party and friend lookups |
| `Rewards` | `economy().deposit` and XP through `profiles().modify` |
| `StatsStore`, `Stats` | a typed collection in `storage()` |
| `StatsDisplay` | a player attribute and a rank source |
| `NetworkStats` | counting a win and a game through `stats()` (pushed stats), a weekly top list, a stat definition and the game metadata of `example` |
| `ArenaStats` | a registered pull source for the game `arena` that reads `StatsStore`, with its own game metadata; its values are mirrored for leaderboards |
| `Moderation` | filing a report, previewing and applying a template, audit entry plus staff alert |
| `EventLog` | `ProfileLoadedEvent`, `BalanceChangedEvent`, `NickChangedEvent`, `ReportFiledEvent`, `ServerChangedEvent`, `StatRecordEvent`, `VoteRewardGivenEvent` |
| `RoundState` | setting the game state |
| `Queueing` | joining a group queue with `/example queue <group>`, closing this server for the queue with `/example lock on`, `QueueSentEvent` and `QueueLeftEvent` |
| `ArenaJoinMe` | a JoinMe handler for arena worlds (placeholders `map` and `mode`, placing players next to the creator, refusing full arenas), `/example joinme` creating a JoinMe through the API, `JoinMeArrivedEvent` |
| `Cosmetics` | a plugin shape (`example-star`) and a cosmetic that uses it, no purchases while a round runs, trails and effects switched on for a round (`/example state running`), `/example cosmetic` playing the win effect, `CosmeticUnlockedEvent` |
| `ExampleCommand`, `Texts` | `/example ...` with texts in the player's language |

## Module documentation

- [`modules/api.md`](modules/api.md): class structure of `:api`
- [`modules/shared.md`](modules/shared.md): storage, message bus, network calls
- [`modules/core-paper.md`](modules/core-paper.md): backend features and their APIs
- [`modules/core-velocity.md`](modules/core-velocity.md): proxy features, reports, punishments, maintenance
- [`modules/lobby.md`](modules/lobby.md): the optional lobby
