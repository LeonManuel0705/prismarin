package net.prismarin.shared.stats;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatChange;
import net.prismarin.api.stats.StatChangedEvent;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.api.stats.StatRecordEvent;
import net.prismarin.api.stats.StatSource;
import net.prismarin.api.stats.StatsService;
import net.prismarin.shared.storage.DocumentStore;

public final class DocumentStatsService implements StatsService {

    public static final String COLLECTION = "player-stats";

    public static final int NAMED_PLACES = 25;

    public static final long DEFAULT_SOURCE_TIMEOUT_MILLIS = 3_000L;

    private static final long MIN_WATCH_MILLIS = TimeUnit.MINUTES.toMillis(10);

    public interface Names {

        Optional<String> known(UUID player);

        CompletableFuture<Map<UUID, String>> shown(List<UUID> players);

        static Names none() {
            return new Names() {
                @Override
                public Optional<String> known(UUID player) {
                    return Optional.empty();
                }

                @Override
                public CompletableFuture<Map<UUID, String>> shown(List<UUID> players) {
                    return CompletableFuture.completedFuture(Map.of());
                }
            };
        }
    }

    public record Settings(StatPeriods periods, long refreshMillis, int cacheSize, boolean sharedRanks,
                           boolean showUnlisted, List<StatDefinition> definitions, List<GameConfig> games,
                           long sourceTimeoutMillis) {

        public Settings {
            Objects.requireNonNull(periods, "periods");
            refreshMillis = Math.max(1_000L, refreshMillis);
            cacheSize = Math.clamp(cacheSize, 1, 1_000);
            definitions = List.copyOf(definitions);
            games = games == null ? List.of() : List.copyOf(games);
            sourceTimeoutMillis = Math.clamp(sourceTimeoutMillis, 100L, 60_000L);
        }

        public Settings(StatPeriods periods, long refreshMillis, int cacheSize, boolean sharedRanks,
                        boolean showUnlisted, List<StatDefinition> definitions) {
            this(periods, refreshMillis, cacheSize, sharedRanks, showUnlisted, definitions, List.of(),
                    DEFAULT_SOURCE_TIMEOUT_MILLIS);
        }

        public static Settings defaults() {
            return new Settings(StatPeriods.defaults(), 60_000L, 100, false, true, List.of());
        }
    }

    private record Board(String key, StatPeriod period) {
    }

    private record Registered(StatGame game, long sequence) {
    }

    private final DocumentStore documents;
    private final Executor executor;
    private final EventBus events;
    private final Clock clock;
    private final Settings settings;
    private final Names names;
    private final Map<String, StatDefinition> configured;
    private final Map<String, StatDefinition> defined = new ConcurrentHashMap<>();
    private final Map<String, GameConfig> gameConfigs;
    private final Map<String, Registered> registeredGames = new ConcurrentHashMap<>();
    private final Map<String, StatSource> sources = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerStats> players = new ConcurrentHashMap<>();
    private final Map<Board, StatSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Board, Long> watched = new ConcurrentHashMap<>();
    private CompletableFuture<Void> refreshing = CompletableFuture.completedFuture(null);
    private volatile BiConsumer<String, Throwable> problems;
    private volatile StatCatalog.Loaded catalog = StatCatalog.Loaded.EMPTY;

    public DocumentStatsService(DocumentStore documents, Executor executor, EventBus events, Clock clock,
                                Settings settings, Names names) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.names = Objects.requireNonNull(names, "names");
        Map<String, StatDefinition> byKey = new LinkedHashMap<>();
        settings.definitions().forEach(definition -> byKey.putIfAbsent(definition.key(), definition));
        this.configured = Collections.unmodifiableMap(byKey);
        Map<String, GameConfig> games = new LinkedHashMap<>();
        settings.games().forEach(game -> games.putIfAbsent(game.id(), game));
        this.gameConfigs = Collections.unmodifiableMap(games);
    }

    public void reportProblems(BiConsumer<String, Throwable> reporter) {
        this.problems = reporter;
    }

    public Settings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<StatChange> increment(UUID player, String key, long amount) {
        return change(player, key, StatChange.Type.INCREMENT, amount);
    }

    @Override
    public CompletableFuture<StatChange> set(UUID player, String key, long value) {
        return change(player, key, StatChange.Type.SET, value);
    }

    @Override
    public CompletableFuture<StatChange> record(UUID player, String key, long value) {
        return change(player, key, StatChange.Type.RECORD, value);
    }

    @Override
    public CompletableFuture<PlayerStats> stats(UUID player) {
        Objects.requireNonNull(player, "player");
        return CompletableFuture.supplyAsync(() -> stored(player), executor).thenCompose(this::withSources);
    }

    public CompletableFuture<PlayerStats> mirror(UUID player, Map<String, Long> values) {
        return mirror(player, values, Map.of(), Map.of());
    }

    public CompletableFuture<PlayerStats> mirror(UUID player, Map<String, Long> all, Map<String, Long> weekly,
                                                 Map<String, Long> monthly) {
        Objects.requireNonNull(player, "player");
        Map<String, Long> cleanAll = clean(all);
        Map<String, Long> cleanWeekly = clean(weekly);
        Map<String, Long> cleanMonthly = clean(monthly);
        return CompletableFuture.supplyAsync(() -> {
            if (cleanAll.isEmpty() && cleanWeekly.isEmpty() && cleanMonthly.isEmpty()) {
                return stored(player);
            }
            Instant now = clock.instant();
            String week = settings.periods().weekly() ? settings.periods().id(StatPeriod.WEEKLY, now) : null;
            String month = settings.periods().monthly() ? settings.periods().id(StatPeriod.MONTHLY, now) : null;
            String name = names.known(player).orElse(null);
            AtomicReference<StatUpdates.Mirrored> applied = new AtomicReference<>();
            documents.update(COLLECTION, player.toString(), current -> {
                StatUpdates.Mirrored result = StatUpdates.mirror(current, player, name, cleanAll, cleanWeekly,
                        cleanMonthly, week, month, now.toEpochMilli());
                applied.set(result);
                return result.json();
            });
            StatUpdates.Mirrored result = applied.get();
            if (result == null) {
                return stored(player);
            }
            result.changes().forEach(change -> events.publish(new StatChangedEvent(change)));
            return result.stats();
        }, executor);
    }

    @Override
    public Optional<PlayerStats> cached(UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(players.get(player));
    }

    public CompletableFuture<PlayerStats> track(UUID player) {
        tracked.add(Objects.requireNonNull(player, "player"));
        return stats(player);
    }

    public void forget(UUID player) {
        if (player != null) {
            tracked.remove(player);
            players.remove(player);
        }
    }

    @Override
    public CompletableFuture<Leaderboard> top(String key, StatPeriod period, int size) {
        Board board = board(key, period);
        if (!settings.periods().enabled(board.period())) {
            return CompletableFuture.completedFuture(empty(board));
        }
        watch(board);
        StatSnapshot ready = current(board, true);
        if (ready != null) {
            return CompletableFuture.completedFuture(ready.board().limit(size));
        }
        return refresh().thenCompose(done -> {
            StatSnapshot first = current(board, false);
            if (first != null) {
                return CompletableFuture.completedFuture(first.board().limit(size));
            }
            return refresh().thenApply(again -> {
                StatSnapshot second = current(board, false);
                return second == null ? empty(board) : second.board().limit(size);
            });
        });
    }

    @Override
    public Optional<Leaderboard> cachedTop(String key, StatPeriod period) {
        Board board = board(key, period);
        if (!settings.periods().enabled(board.period())) {
            return Optional.empty();
        }
        watch(board);
        if (current(board, true) == null) {
            refresh();
        }
        return Optional.ofNullable(current(board, false)).map(StatSnapshot::board);
    }

    @Override
    public CompletableFuture<OptionalInt> rank(UUID player, String key, StatPeriod period) {
        Objects.requireNonNull(player, "player");
        Board board = board(key, period);
        if (!settings.periods().enabled(board.period())) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
        return top(board.key(), board.period(), settings.cacheSize()).thenCombine(stats(player), (top, stats) -> {
            StatSnapshot snapshot = current(board, false);
            Long value = stats.values(board.period()).get(board.key());
            return snapshot == null ? OptionalInt.empty() : snapshot.rank(player, value);
        });
    }

    @Override
    public OptionalInt cachedRank(UUID player, String key, StatPeriod period) {
        if (player == null) {
            return OptionalInt.empty();
        }
        Board board = board(key, period);
        if (cachedTop(board.key(), board.period()).isEmpty()) {
            return OptionalInt.empty();
        }
        StatSnapshot snapshot = current(board, false);
        Long value = cached(player).map(stats -> stats.values(board.period()).get(board.key())).orElse(null);
        return snapshot == null ? OptionalInt.empty() : snapshot.rank(player, value);
    }

    @Override
    public Set<StatPeriod> periods() {
        return settings.periods().active();
    }

    @Override
    public String periodId(StatPeriod period) {
        return settings.periods().id(Objects.requireNonNull(period, "period"), clock.instant());
    }

    @Override
    public StatDefinition definition(String key) {
        String normalized = StatKeys.key(key);
        StatDefinition found = configured.get(normalized);
        if (found != null) {
            return found;
        }
        found = defined.get(normalized);
        if (found != null) {
            return found;
        }
        found = catalog.definitions().get(normalized);
        if (found != null) {
            return found;
        }
        return new StatDefinition(normalized, null, StatFormat.guess(normalized), StatOrder.HIGHEST,
                settings.showUnlisted(), false);
    }

    public boolean known(String key) {
        String normalized = StatKeys.key(key);
        return configured.containsKey(normalized) || defined.containsKey(normalized)
                || catalog.definitions().containsKey(normalized);
    }

    @Override
    public List<StatDefinition> definitions() {
        List<StatDefinition> all = new ArrayList<>(configured.values());
        defined.values().stream()
                .filter(definition -> !configured.containsKey(definition.key()))
                .sorted((first, second) -> first.key().compareTo(second.key()))
                .forEach(all::add);
        catalog.definitions().values().stream()
                .filter(definition -> !configured.containsKey(definition.key())
                        && !defined.containsKey(definition.key()))
                .sorted((first, second) -> first.key().compareTo(second.key()))
                .forEach(all::add);
        return List.copyOf(all);
    }

    @Override
    public Subscription define(StatDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        defined.put(definition.key(), definition);
        share(StatCatalog.id(definition), StatCatalog.encode(definition));
        return () -> defined.remove(definition.key(), definition);
    }

    @Override
    public StatGame game(String id) {
        String normalized = StatKeys.gameId(id);
        Registered registered = registeredGames.get(normalized);
        StatGame base = registered != null ? registered.game()
                : catalog.games().getOrDefault(normalized, StatGame.of(normalized));
        GameConfig config = gameConfigs.get(normalized);
        return config == null ? base : config.apply(base);
    }

    @Override
    public List<StatGame> games() {
        Set<String> ids = new LinkedHashSet<>(gameConfigs.keySet());
        registeredGames.values().stream()
                .sorted(Comparator.comparingLong(Registered::sequence))
                .forEach(registered -> ids.add(registered.game().id()));
        ids.addAll(catalog.games().keySet());
        definitions().forEach(definition -> ids.add(definition.game()));
        sources.keySet().stream().sorted().forEach(ids::add);
        List<StatGame> games = new ArrayList<>();
        ids.forEach(id -> games.add(game(id)));
        games.sort(Comparator.comparingInt(StatGame::order));
        return List.copyOf(games);
    }

    @Override
    public Subscription defineGame(StatGame game) {
        Objects.requireNonNull(game, "game");
        Registered registered = new Registered(game, sequence.incrementAndGet());
        registeredGames.put(game.id(), registered);
        share(StatCatalog.id(game), StatCatalog.encode(game));
        return () -> registeredGames.remove(game.id(), registered);
    }

    @Override
    public Subscription registerSource(StatSource source) {
        Objects.requireNonNull(source, "source");
        String game = StatKeys.gameId(source.game());
        sources.put(game, source);
        dropBoards(game);
        return () -> {
            if (sources.remove(game, source)) {
                dropBoards(game);
            }
        };
    }

    public CompletableFuture<StatCatalog.Loaded> loadCatalog() {
        return CompletableFuture.supplyAsync(() -> {
            StatCatalog.Loaded loaded = StatCatalog.decode(documents.findAll(StatCatalog.COLLECTION));
            catalog = loaded;
            return loaded;
        }, executor);
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Optional<StatSource> source(String game) {
        return StatKeys.validGame(game) ? Optional.ofNullable(sources.get(StatKeys.gameId(game)))
                : Optional.empty();
    }

    public CompletableFuture<Map<String, OptionalInt>> ranks(PlayerStats values, Collection<String> keys,
                                                             StatPeriod period) {
        Objects.requireNonNull(values, "values");
        StatPeriod chosen = period == null ? StatPeriod.ALL_TIME : period;
        Map<String, CompletableFuture<OptionalInt>> pending = new LinkedHashMap<>();
        for (String key : keys) {
            Board board = board(key, chosen);
            if (!settings.periods().enabled(chosen)) {
                pending.put(board.key(), CompletableFuture.completedFuture(OptionalInt.empty()));
                continue;
            }
            pending.put(board.key(), top(board.key(), chosen, settings.cacheSize()).thenApply(top -> {
                StatSnapshot snapshot = current(board, false);
                Long value = values.values(chosen).get(board.key());
                return snapshot == null ? OptionalInt.empty() : snapshot.rank(values.player(), value);
            }).exceptionally(error -> OptionalInt.empty()));
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).thenApply(done -> {
            Map<String, OptionalInt> ranks = new LinkedHashMap<>();
            pending.forEach((key, future) -> ranks.put(key, future.join()));
            return ranks;
        });
    }

    public synchronized CompletableFuture<Void> refresh() {
        if (!refreshing.isDone()) {
            return refreshing;
        }
        CompletableFuture<Void> next = CompletableFuture.supplyAsync(this::rebuild, executor)
                .thenCompose(work -> work);
        refreshing = next;
        return next;
    }

    public boolean watching() {
        return !watched.isEmpty();
    }

    private CompletableFuture<Void> rebuild() {
        long now = clock.millis();
        long expiry = Math.max(MIN_WATCH_MILLIS, settings.refreshMillis() * 5L);
        watched.entrySet().removeIf(entry -> now - entry.getValue() > expiry);
        snapshots.keySet().removeIf(board -> !watched.containsKey(board));
        if (watched.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Instant instant = Instant.ofEpochMilli(now);
        Map<StatPeriod, String> ids = new EnumMap<>(StatPeriod.class);
        for (StatPeriod period : StatPeriod.values()) {
            ids.put(period, settings.periods().id(period, instant));
        }
        List<Board> stored = new ArrayList<>();
        List<CompletableFuture<Void>> work = new ArrayList<>();
        for (Board board : watched.keySet()) {
            Optional<StatSource> source = ranked(board.key());
            if (source.isPresent()) {
                work.add(rebuildSourced(board, source.get(), ids.get(board.period()), now));
            } else {
                stored.add(board);
            }
        }
        if (!stored.isEmpty()) {
            work.add(rebuildStored(stored, ids, now));
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> rebuildStored(List<Board> boards, Map<StatPeriod, String> ids, long now) {
        Map<Board, List<TopList.Row>> rows = new HashMap<>();
        boards.forEach(board -> rows.put(board, new ArrayList<>()));
        for (String json : documents.findAll(COLLECTION)) {
            StatDocument document = StatDocument.decode(json);
            UUID uuid = document.uuid();
            if (uuid == null) {
                continue;
            }
            for (Map.Entry<Board, List<TopList.Row>> entry : rows.entrySet()) {
                Board board = entry.getKey();
                String id = ids.get(board.period());
                Long value = document.values(board.period(), id).get(board.key());
                if (value != null) {
                    long at = document.times(board.period(), id).getOrDefault(board.key(), 0L);
                    entry.getValue().add(new TopList.Row(uuid, document.name, value, at));
                }
            }
        }
        Map<Board, List<TopList.Row>> sorted = new HashMap<>();
        Set<UUID> wanted = new LinkedHashSet<>();
        rows.forEach((board, list) -> {
            List<TopList.Row> ordered = TopList.sort(list, definition(board.key()).order());
            sorted.put(board, ordered);
            ordered.stream().limit(Math.min(settings.cacheSize(), NAMED_PLACES))
                    .forEach(row -> wanted.add(row.player()));
        });
        return names.shown(List.copyOf(wanted))
                .exceptionally(error -> Map.of())
                .thenAccept(shown -> rows.forEach((board, list) -> {
                    StatOrder order = definition(board.key()).order();
                    Leaderboard leaderboard = new Leaderboard(board.key(), board.period(), ids.get(board.period()),
                            TopList.entries(sorted.get(board), settings.cacheSize(), settings.sharedRanks(),
                                    shown == null ? Map.of() : shown), now);
                    snapshots.put(board, new StatSnapshot(leaderboard, TopList.values(list), order,
                            settings.sharedRanks()));
                }));
    }

    private CompletableFuture<Void> rebuildSourced(Board board, StatSource source, String periodId, long now) {
        if (!supports(source, board.period())) {
            snapshots.put(board, StatSnapshot.listed(new Leaderboard(board.key(), board.period(), periodId,
                    List.of(), now)));
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<List<LeaderboardEntry>> asked;
        try {
            asked = source.top(board.key(), board.period(), settings.cacheSize());
        } catch (RuntimeException e) {
            asked = CompletableFuture.failedFuture(e);
        }
        if (asked == null) {
            asked = CompletableFuture.completedFuture(List.of());
        }
        return asked.copy().orTimeout(settings.sourceTimeoutMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(given -> {
                    List<LeaderboardEntry> entries = StatSources.entries(given, settings.cacheSize());
                    List<UUID> wanted = entries.stream().limit(NAMED_PLACES).map(LeaderboardEntry::player).toList();
                    return names.shown(wanted).exceptionally(error -> Map.of()).thenAccept(shown ->
                            snapshots.put(board, StatSnapshot.listed(new Leaderboard(board.key(), board.period(),
                                    periodId, StatSources.named(entries, shown), now))));
                })
                .exceptionally(error -> {
                    report(source.game(), error);
                    return null;
                });
    }

    private PlayerStats stored(UUID player) {
        Instant now = clock.instant();
        return StatUpdates.read(documents.find(COLLECTION, player.toString()).orElse(null), player,
                periodIdOrEmpty(StatPeriod.WEEKLY, now), periodIdOrEmpty(StatPeriod.MONTHLY, now));
    }

    private CompletableFuture<PlayerStats> withSources(PlayerStats stored) {
        List<StatSource> current = List.copyOf(sources.values());
        if (current.isEmpty()) {
            return CompletableFuture.completedFuture(remember(stored));
        }
        List<CompletableFuture<Optional<StatSources.Read>>> reads = new ArrayList<>();
        for (StatSource source : current) {
            reads.add(read(source, stored.player()));
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenCompose(done -> {
            List<StatSources.Read> found = reads.stream().map(CompletableFuture::join).flatMap(Optional::stream)
                    .toList();
            StatSources.Plan plan = StatSources.changes(stored, found);
            CompletableFuture<PlayerStats> base = plan.empty() ? CompletableFuture.completedFuture(stored)
                    : mirror(stored.player(), plan.all(), plan.weekly(), plan.monthly()).exceptionally(error -> {
                        report("", error);
                        return stored;
                    });
            return base.thenApply(mirrored -> remember(StatSources.merge(mirrored, found)));
        });
    }

    private CompletableFuture<Optional<StatSources.Read>> read(StatSource source, UUID player) {
        String game = StatKeys.gameId(source.game());
        boolean ranked = ranked(source);
        CompletableFuture<Map<String, Long>> all = ask(() -> source.values(player));
        CompletableFuture<Map<String, Long>> weekly = settings.periods().weekly() && supports(source, StatPeriod.WEEKLY)
                ? ask(() -> source.values(player, StatPeriod.WEEKLY)) : CompletableFuture.completedFuture(Map.of());
        CompletableFuture<Map<String, Long>> monthly = settings.periods().monthly()
                && supports(source, StatPeriod.MONTHLY)
                ? ask(() -> source.values(player, StatPeriod.MONTHLY)) : CompletableFuture.completedFuture(Map.of());
        return CompletableFuture.allOf(all, weekly, monthly)
                .orTimeout(settings.sourceTimeoutMillis(), TimeUnit.MILLISECONDS)
                .handle((done, error) -> {
                    if (error != null) {
                        report(game, error);
                        return Optional.empty();
                    }
                    return Optional.of(new StatSources.Read(game, ranked, all.join(), weekly.join(), monthly.join()));
                });
    }

    private static CompletableFuture<Map<String, Long>> ask(Supplier<CompletableFuture<Map<String, Long>>> call) {
        try {
            CompletableFuture<Map<String, Long>> future = call.get();
            return future == null ? CompletableFuture.completedFuture(Map.of())
                    : future.thenApply(values -> values == null ? Map.<String, Long>of() : values);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Optional<StatSource> ranked(String key) {
        StatSource source = sources.get(StatKeys.game(key));
        return source != null && ranked(source) ? Optional.of(source) : Optional.empty();
    }

    private boolean ranked(StatSource source) {
        try {
            return source.topLists();
        } catch (RuntimeException e) {
            report(source.game(), e);
            return false;
        }
    }

    private boolean supports(StatSource source, StatPeriod period) {
        if (period == StatPeriod.ALL_TIME) {
            return true;
        }
        try {
            Set<StatPeriod> periods = source.periods();
            return periods != null && periods.contains(period);
        } catch (RuntimeException e) {
            report(source.game(), e);
            return false;
        }
    }

    private PlayerStats remember(PlayerStats stats) {
        if (tracked.contains(stats.player())) {
            players.put(stats.player(), stats);
        }
        return stats;
    }

    private void share(String id, String json) {
        CompletableFuture.runAsync(() -> {
            if (!documents.find(StatCatalog.COLLECTION, id).map(json::equals).orElse(false)) {
                documents.save(StatCatalog.COLLECTION, id, json);
            }
        }, executor).exceptionally(error -> {
            report("", error);
            return null;
        });
    }

    private void dropBoards(String game) {
        snapshots.keySet().removeIf(board -> StatKeys.game(board.key()).equals(game));
    }

    private void report(String game, Throwable error) {
        BiConsumer<String, Throwable> reporter = problems;
        if (reporter != null) {
            reporter.accept(game, error);
        }
    }

    private static Map<String, Long> clean(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> clean = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null && StatKeys.valid(key)) {
                clean.put(StatKeys.key(key), value);
            }
        });
        return Collections.unmodifiableMap(clean);
    }

    private CompletableFuture<StatChange> change(UUID player, String key, StatChange.Type type, long amount) {
        Objects.requireNonNull(player, "player");
        String normalized = StatKeys.key(key);
        StatOrder order = definition(normalized).order();
        return CompletableFuture.supplyAsync(() -> {
            Instant now = clock.instant();
            String week = settings.periods().weekly() ? settings.periods().id(StatPeriod.WEEKLY, now) : null;
            String month = settings.periods().monthly() ? settings.periods().id(StatPeriod.MONTHLY, now) : null;
            String name = names.known(player).orElse(null);
            AtomicReference<StatUpdates.Applied> applied = new AtomicReference<>();
            documents.update(COLLECTION, player.toString(), current -> {
                StatUpdates.Applied result = StatUpdates.apply(current, player, name, normalized, type, amount, order,
                        week, month, now.toEpochMilli());
                applied.set(result);
                return result.json();
            });
            StatUpdates.Applied result = applied.get();
            if (tracked.contains(player)) {
                players.put(player, result.stats());
            }
            if (result.change().changed() || result.improved()) {
                events.publish(new StatChangedEvent(result.change()));
            }
            if (result.improved()) {
                events.publish(new StatRecordEvent(player, normalized, result.change().previous(),
                        result.change().value()));
            }
            return result.change();
        }, executor);
    }

    private Board board(String key, StatPeriod period) {
        return new Board(StatKeys.key(key), period == null ? StatPeriod.ALL_TIME : period);
    }

    private void watch(Board board) {
        watched.put(board, clock.millis());
    }

    private StatSnapshot current(Board board, boolean fresh) {
        StatSnapshot snapshot = snapshots.get(board);
        if (snapshot == null) {
            return null;
        }
        if (!snapshot.board().periodId().equals(settings.periods().id(board.period(), clock.instant()))) {
            return null;
        }
        if (fresh && clock.millis() - snapshot.board().updatedAt() >= settings.refreshMillis()) {
            return null;
        }
        return snapshot;
    }

    private Leaderboard empty(Board board) {
        return new Leaderboard(board.key(), board.period(), settings.periods().id(board.period(), clock.instant()),
                List.of(), clock.millis());
    }

    private String periodIdOrEmpty(StatPeriod period, Instant now) {
        return settings.periods().enabled(period) ? settings.periods().id(period, now) : "";
    }
}
