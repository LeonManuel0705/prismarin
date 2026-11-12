# Modul `:api`

## Anforderungen

- Plattformneutrale Verträge für alle Netzwerk-Dienste: kein Paper, kein Velocity, kein Guice, keine Treiber.
- Externe Abhängigkeiten sind nur Adventure (`adventure-api`, `adventure-text-minimessage`) und Gson (für `TypedCollection`), beide `compileOnly`, weil beide Plattformen sie bündeln.
- Veröffentlicht als `net.prismarin:prismarin-api` mit Quellen-Jar (`maven-publish`, Publikation `api`, Ziel `GitHubPackages`, JitPack über `jitpack.yml`).
- Alle I/O-Verträge sind asynchron (`CompletableFuture`); Implementierungen liegen in `:shared`.
- Diese API ist der Vertrag für fremde Plugins. Sie darf sich nur mit einer neuen Minor-Version brechen, solange die Version unter 1.0 liegt.

Die englische Übersicht aller Dienste mit Beispielen und Koordinaten steht in [`docs/api.md`](../api.md).

## Einstieg für fremde Plugins

```java
Prismarin prismarin = PrismarinProvider.get();
Currency coins = prismarin.economy().primaryCurrency();
prismarin.economy().deposit(player.getUniqueId(), coins, 50);
prismarin.events().subscribe(BalanceChangedEvent.class, event -> log(event.newBalance()));
```

Auf Paper liegt die Instanz zusätzlich im ServicesManager (`Bukkit.getServicesManager().load(Prismarin.class)`). Das Plugin braucht `depend: [PrismarinCore]` in der `plugin.yml`, auf Velocity eine Abhängigkeit auf `prismarin-core`.

## Klassenstruktur

```
net.prismarin.api
├── Prismarin                serverId, branding, profiles, economy, locale, messageBus, servers, serverStatus, events,
│                            enabledSystems(), systemEnabled(name)
│                            players, friends, parties, reports, punishmentTemplates, nicks, storage, audit,
│                            maintenance, notifications, levels, vanish, staffMode, chatFilter, alts,
│                            stats, dailyRewards, votes, queue
│                            (Standardmethoden über die Halter unten)
├── PrismarinSystems         ECONOMY, LEVELS, NETWORK_STATUS
├── PrismarinProvider        get(), isAvailable(); register/unregister nur durch die Core-Plugins
├── admin
│   ├── AdminApi             Halter für AuditService, MaintenanceService, NotificationService
│   ├── AuditService         record(actor, action, target, details), latest([target,] limit)
│   ├── AuditEntry           Record, check(...) mit Grenzen, limit(n) höchstens 100
│   ├── MaintenanceService   state(), setNetwork, setServer, setGroup (liefern, ob sich etwas geändert hat)
│   ├── MaintenanceState     network, servers, groups, of(network, ziele), blocks(server, gruppe), targets()
│   ├── NotificationService  notifyStaff(StaffAlert), staffChat(StaffChat), broadcast(Broadcast) -> Delivery
│   ├── NoticeText           Texte je Sprache (*, Tag, Sprache, en) und Platzhalter als Rohtext
│   ├── StaffAlert, StaffChat, Broadcast, Delivery
│   └── MaintenanceChangedEvent, StaffAlertEvent
├── brand
│   └── Branding             Record (name, primaryColor, accentColor, prefix), DEFAULT, gradient(), title()
├── economy
│   ├── Currency             Record (id), COINS und GEMS als Standard, of(id), messageKey()
│   ├── EconomyResult        Record (success, newBalance, failureReason), UNKNOWN_CURRENCY, SYSTEM_DISABLED
│   ├── EconomyService       enabled(), currencies(), primaryCurrency(), currency(id), balance/deposit/withdraw/set atomar
│   └── BalanceChangedEvent  nach jeder erfolgreichen Transaktion (DEPOSIT, WITHDRAW, SET)
├── event
│   ├── EventBus             subscribe(type, handler) -> Subscription, publish(event)
│   ├── PrismarinEvent       Marker
│   └── Subscription         unsubscribe()
├── level
│   ├── LevelService         progress, find, addXp, setLevel, addLevels, requirement; REASON_PLUGIN, REASON_COMMAND
│   ├── LevelProgress        Record (level, xp, required), remaining(), fraction(), requirement(level), MAX_LEVEL
│   ├── LevelChange          Record (player, before, after, xpAdded, reason), levelChanged(), levelledUp()
│   ├── LevelChangedEvent    level went up or down (any cause)
│   ├── XpGainedEvent        XP was added (before, after, reason)
│   └── LevelApi             holder behind Prismarin.levels()
├── locale
│   └── LocaleService        render(key, locale, resolvers), raw(key, locale), format(text, resolvers)
├── messaging
│   ├── MessageBus           publish/subscribe/close/serverId, JSON-Payloads
│   └── NetworkMessage       Record-Envelope
├── moderation
│   ├── ModerationApi        holders behind reports(), punishmentTemplates(), chatFilter(), alts()
│   ├── ChatFilterService    check(text), filter(ChatMessage), rules(), registerRule, unregisterRule
│   ├── ChatFilterRule       id(), check(ChatMessage) -> Optional<ChatFilterMatch>, ID pattern
│   ├── ChatMessage          sender, senderName, channel (PUBLIC, PRIVATE, PARTY, TEXT or own), text
│   ├── ChatFilterMatch      rule, action, detail, text; block, warn, log, replace, as(action)
│   ├── ChatFilterAction     BLOCK, REPLACE, WARN, LOG
│   ├── ChatFilterResult     original, text, blocked, matches; clean, changed, warned, blocking, rules
│   ├── ChatFilteredEvent    cancellable, on the server that filtered the message
│   ├── ChatFilterEscalatedEvent  proxy, a rule reached its escalation threshold
│   ├── AltService           alts(uuid), bannedAlts(uuid), related(first, second)
│   ├── AltAccount           uuid, name, firstSeen, lastSeen, sharedAddresses, banned, muted
│   ├── BannedAltJoinEvent   proxy, an account sharing an address with a banned one joined
│   └── Punishment, Report and template types (see core-velocity.md)
├── nick
│   ├── NickService          isNicked, shownNick, realName, nick, nickRandom, unnick, autoNick, Runden-Marker
│   ├── NickApi              Halter hinter Prismarin.nicks()
│   ├── NickResult           Status und Name
│   └── NickChangedEvent
├── player
│   ├── PlayerService        players(): online, find, findAll, findByRealName, locale, displayName, rank, canSee,
│   │                        resolve, resolveOnline, suggestions, attributes, party/friend links
│   ├── NetworkPlayer        snapshot: real name, nick, locale, server, world, online, vanished, rank, realRank, level
│   ├── RankDisplay          label, prefix, suffix, nameColor, chatColor, weight, NONE
│   ├── ViewerRules          pure nick and vanish rules for names, ranks, resolution and suggestions
│   ├── PlayerAttribute      extension point for per player data of other plugins
│   ├── PlayerAttributes     registry: register -> Subscription, value(s), profileLine(s)
│   ├── PlayerApi            holder behind Prismarin.players()
│   ├── ServerChangedEvent   player switched or joined a server
│   └── LocaleChangedEvent   chosen language changed
├── profile
│   ├── PlayerProfile        uuid, name, firstJoin, lastSeen, locale, balances je Currency, level, xp, settings,
│   │                        progress(), addXp(amount, reason), setLevel(level, reason), setProgress(...)
│   ├── ProfileService       load/getCached/save/unload/modify
│   ├── ProfileLoadedEvent   nach dem Laden (created = Erstjoin)
│   └── ProfileUnloadedEvent nach Speichern und Entladen
├── queue
│   ├── QueueService         join, leave, positions, position, size, queues, queue, pause, resume, clear, sendNext,
│   │                        joinable(server, joinable), closedServers
│   ├── QueueTarget          Record (kind GROUP/SERVER, name), group(name), server(name), parse("group:x"), id()
│   ├── QueueResult          Record (status, server, position), sent(), waiting()
│   ├── QueueStatus          SENT, QUEUED, ALREADY_QUEUED, ALREADY_THERE, NOT_LEADER, UNKNOWN_TARGET, NO_SERVER,
│   │                        OFFLINE, CANCELLED, DISABLED, FAILED
│   ├── QueuePosition        Record (target, owner, party, position, size, players, priority, paused, joinedAt, online)
│   ├── QueueInfo, QueueEntry  Übersicht einer Warteschlange und ein Eintrag darin
│   ├── QueueJoinedEvent, QueueLeftEvent (Reason), QueueSentEvent, QueuePausedEvent
│   ├── QueueJoinEvent, QueueSendEvent  abbrechbar, nur auf dem Proxy
│   └── QueueApi             holder behind Prismarin.queue(), BYPASS_PERMISSION
├── server
│   ├── NetworkServer        Record (name, displayName, host, port, group, properties), GROUP_LOBBY, GROUP_DEFAULT
│   ├── ServerDirectory      servers(), byName(name), byGroup(group)
│   ├── ServerStatus         online, players, maxPlayers, latencyMillis, version, prismarin, tps, mspt, RAM, uptime
│   └── ServerStatusService  statuses(), status(name), refresh()
├── reward
│   ├── RewardApi            Halter für DailyRewardService und VoteService, handlers()
│   ├── Reward               Record (currencies, xp, levels, commands, messages, custom), EMPTY, plus(), Fabriken
│   ├── RewardContext        Record (source daily/vote, player, name, streak, detail)
│   ├── RewardHandler, RewardHandlers  eigene Belohnungen per id, global()
│   ├── DailyRewardService   status, cached, claim, reward(streak), cycle
│   ├── DailyStatus, DailyClaim (Result CLAIMED/ALREADY_CLAIMED)
│   ├── DailyRewardClaimedEvent, DailyStreakBrokenEvent
│   ├── VoteService          sites, vote(name|uuid, service), stats, pending, claim
│   ├── Vote, VoteSite, VoteStats
│   └── VoteReceivedEvent, VoteRewardGivenEvent
├── staff
│   ├── StaffModeService     active, enter, leave, toggle, nickIncluded, vanishIncluded
│   ├── StaffModeChangedEvent
│   └── StaffModeApi         holder behind Prismarin.staffMode()
├── stats
│   ├── StatsApi             Halter hinter Prismarin.stats()
│   ├── StatsService         increment, set, record, stats, cached, top, cachedTop, rank, cachedRank, periods,
│   │                        periodId, definition(s), define, format, game(id), games(), defineGame,
│   │                        registerSource, source(game)
│   ├── StatKeys             [a-z0-9][a-z0-9.-]{0,63}, game(key), humanize(key), gameId, gameName, inGame
│   ├── StatPeriod, StatOrder, StatFormat (number, plain, duration, millis, decimal, guess(key))
│   ├── StatDefinition       Record (key, name, format, order, inStats, inProfile)
│   ├── StatGame             Record (id, name, icon, color, order, featured), of, withName, withIcon, ...
│   ├── StatSource           pull source: game, values(player[, period]), periods, topLists, top, of(game, fn)
│   ├── PlayerStats, StatChange, Leaderboard, LeaderboardEntry
│   └── StatChangedEvent, StatRecordEvent
├── storage
│   ├── DataStorage          namespace(name) -> DataNamespace
│   ├── DataNamespace        collection(name) roh, collection(name, Typ[, Gson]) typisiert
│   ├── DataCollection       save, find, delete, list, findBy, ids, exists (alles asynchron)
│   ├── TypedCollection      Gson-Abbildung auf einer DataCollection
│   ├── StorageNames         Regeln für Namensräume, Collections und IDs
│   └── StorageApi           Halter hinter Prismarin.storage()
└── vanish
    ├── VanishService        vanished, vanishedPlayers, level, canSee, vanish, reveal, toggle
    ├── VanishChangedEvent   Record (player, vanished, level, cause COMMAND/STAFF_MODE/JOIN/PLUGIN)
    └── VanishApi            holder behind Prismarin.vanish()
```

## Abgeschaltete Systeme

Ist `economy` aus, liefert `economy()` eine Attrappe: `enabled()` ist `false`, `currencies()` leer, `primaryCurrency()` wirft `IllegalStateException`, Buchungen scheitern mit `SYSTEM_DISABLED`. Plugins prüfen vorher `systemEnabled(PrismarinSystems.ECONOMY)`.

## Events

Laufen synchron auf dem Thread, der sie auslöst; für Profile und Economy ist das ein I/O-Thread. Vor Bukkit- oder Velocity-Zugriffen auf den Main-Thread wechseln. Ein Handler, der wirft, hält die anderen nicht auf. Events gelten nur für den Server, auf dem die Änderung passiert ist.

## Levels and XP

`PrismarinProvider.get().levels()` returns an `Optional<LevelService>`. It is empty while `systems.levels` is `false` in `config.yml`, so a game plugin rewards XP in one line and does nothing on networks without levels:

```java
Prismarin prismarin = PrismarinProvider.get();
prismarin.levels().ifPresent(levels -> levels.addXp(player.getUniqueId(), 50, "bedwars-win"));

prismarin.levels().ifPresent(levels -> levels.addXp(winner, 250, "bedwars-win").thenAccept(change -> {
    if (change.levelledUp()) {
        Bukkit.getScheduler().runTask(plugin, () -> celebrate(winner, change.after().level()));
    }
}));

LevelProgress progress = prismarin.levels().flatMap(levels -> levels.progress(uuid)).orElse(LevelProgress.of(0, 0));
long missing = progress.remaining();
double bar = progress.fraction();
long nextStep = prismarin.levels().map(levels -> levels.requirement(progress.level())).orElse(0L);
```

| Method | Meaning |
| --- | --- |
| `progress(uuid)` | level, XP and requirement of a player whose profile is loaded on this server, synchronous |
| `find(uuid)` | the same for any stored player, as a future |
| `addXp(uuid, amount, reason)` | adds XP and levels up as often as the XP allows. Negative amounts fail with `IllegalArgumentException` |
| `setLevel(uuid, level, reason)` | sets the level and resets the XP to 0, like `/level <player> set` |
| `addLevels(uuid, levels, reason)` | adds or (negative) removes whole levels and keeps the XP |
| `requirement(level)` | XP needed from `level` to the next one: `500 + level * 250` |

Writes go through `ProfileService.modify`, so they work for offline players too and complete off the main thread with a `LevelChange` (progress before and after). Levels stay between 0 and `LevelProgress.MAX_LEVEL`. The reason is free text for statistics (`bedwars-win`, `quest`), blank means `plugin`, the `/level` command uses `command`.

Events on the `EventBus`, published on the thread that changed the profile (usually an I/O thread):

| Event | When |
| --- | --- |
| `XpGainedEvent(player, amount, reason, before, after)` | XP was added, `levelledUp()` tells whether a level was reached |
| `LevelChangedEvent(player, previousLevel, level, reason)` | the level changed for any reason, after the XP event of the same change |

They fire for every change of a profile handed out by the profile service: the `LevelService`, the `/level` command and direct calls of `PlayerProfile.addXp`, `setLevel` or `setProgress` on a cached or modified profile. `setXp` alone fires nothing. The level service exists on Paper and on the proxy.

```java
prismarin.events().subscribe(LevelChangedEvent.class, event -> {
    if (event.levelledUp()) {
        Bukkit.getScheduler().runTask(plugin, () -> rewards.give(event.player(), event.level()));
    }
});
```

## Vanish and staff mode

Both are platform neutral interfaces with the Paper implementation of `PrismarinCore`; on the proxy `vanish()` and `staffMode()` are empty. Details and examples are in the Paper section of `core-paper.md`.

- `PrismarinProvider.get().vanish()`: `Optional<VanishService>`, empty when the `vanish` module is off.
- `PrismarinProvider.get().staffMode()`: `Optional<StaffModeService>`, empty when the staff mode is off.
- `VanishChangedEvent` and `StaffModeChangedEvent` fire on the main thread.

## Tests

`StatModelTest`, `StatGameTest` (fallback names, game metadata, guessed formats, lambda sources), `PlayerProfileTest`, `CurrencyTest`, `BrandingTest`, `ViewerRulesTest`, `NetworkPlayerTest`, `PlayerAttributesTest`, `PlayerProfileProgressTest` (progress listener rules), `LevelProgressTest`, `VanishApiTest` (holders, toggle defaults, event level), `StorageNamesTest`, `TypedCollectionTest`, `NoticeTextTest`, `AdminModelTest`, `QueueTargetTest` (targets, ids, results).

## JoinMe and skin faces

`net.prismarin.api.joinme`: `JoinMeService` (behind `Prismarin.joinMe()`, holder `JoinMeApi`), `JoinMe`, `JoinMeSpot`, `JoinMeAudience`, `JoinMeRequest`, `JoinMeResult`, `JoinMeHandler`, `JoinMeScope` (`server`, `groups`, `worlds`, `when`), `JoinMeArrival`, `JoinMeOutcome` and the events `JoinMeCreateEvent`, `JoinMeCreatedEvent`, `JoinMeJoinEvent`, `JoinMeArrivedEvent`, `JoinMeExpiredEvent`, `JoinMePreviewEvent` (placeholders and face rows of a `/joinme preview`, never cancellable). `JoinMeService.preview(uuid)` and `preview(JoinMeRequest)` send the broadcast only to the creator. `net.prismarin.api.skin`: `SkinFaces` (behind `Prismarin.skinFaces()`, holder `SkinFaceApi`) and `ChatFace`. Usage in [`docs/api.md`](../api.md#joinme). Tests: `JoinMeModelTest`, `ChatFaceTest`.


## Cosmetics

`net.prismarin.api.cosmetics`: `CosmeticService` (behind `Prismarin.cosmetics()`, holder `CosmeticApi`), `Cosmetic`, `CosmeticSlot` (`TRAIL`, `AURA`, `HAT`, `PROJECTILE_TRAIL`, `KILL_EFFECT`, `WIN_EFFECT` or own ids), `CosmeticLook` with `CosmeticParticles` and `CosmeticHat`, `CosmeticPrice`, `CosmeticRarity`, `CosmeticRenderer`, `CosmeticFrame`, `CosmeticPoint`, `CosmeticSpot`, `CosmeticViewFilter`, `PlayerCosmetics`, `OwnedCosmetic`, `CosmeticSource`, `CosmeticResult`, the effect kinds `CosmeticEffectKind` and `CosmeticEffectFrame` (`CosmeticParticles` carries `kind`, `duration` and `variant`), `CosmeticApi.effectEntity(uuid)` and the events `CosmeticPurchaseEvent`, `CosmeticEquipEvent`, `CosmeticEffectEvent` (all cancellable, the last with its `Trigger`), `CosmeticUnlockedEvent`, `CosmeticUnequippedEvent`. Usage in [`docs/api.md`](../api.md#cosmetics). Tests: `CosmeticModelTest`.
