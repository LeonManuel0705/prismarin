package net.prismarin.core.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatGame;

public final class StatsView {

    public record Group(String game, List<StatDefinition> stats) {

        public Group {
            stats = List.copyOf(stats);
        }
    }

    public enum Layout {
        STATS,
        GAMES
    }

    public record Sheet(Layout layout, List<Group> groups, int page, int pages) {

        public Sheet {
            groups = List.copyOf(groups);
        }

        public List<String> keys() {
            return groups.stream().flatMap(group -> group.stats().stream()).map(StatDefinition::key).toList();
        }
    }

    private StatsView() {
        throw new AssertionError();
    }

    public static List<Group> groups(PlayerStats stats, List<StatDefinition> configured,
                                     Function<String, StatDefinition> definitions, List<StatGame> games,
                                     String onlyGame) {
        List<StatDefinition> shown = new ArrayList<>();
        for (StatDefinition definition : configured) {
            if (definition.inStats() && stats.has(definition.key())) {
                shown.add(definition);
            }
        }
        for (String key : stats.keys()) {
            if (configured.stream().noneMatch(definition -> definition.key().equals(key))) {
                StatDefinition definition = definitions.apply(key);
                if (definition.inStats()) {
                    shown.add(definition);
                }
            }
        }
        Map<String, StatGame> meta = new HashMap<>();
        Map<String, List<StatDefinition>> byGame = new LinkedHashMap<>();
        for (StatGame game : games.stream().sorted(Comparator.comparingInt(StatGame::order)).toList()) {
            meta.putIfAbsent(game.id(), game);
            byGame.putIfAbsent(game.id(), new ArrayList<>());
        }
        for (StatDefinition definition : shown) {
            byGame.computeIfAbsent(definition.game(), game -> new ArrayList<>()).add(definition);
        }
        List<Group> groups = new ArrayList<>();
        byGame.forEach((game, list) -> {
            if (!list.isEmpty() && (onlyGame == null || onlyGame.equals(game))) {
                StatGame known = meta.get(game);
                List<StatDefinition> ordered = new ArrayList<>(list);
                if (known != null && !known.featured().isEmpty()) {
                    ordered.sort(Comparator.comparingInt(definition -> known.featuredIndex(definition.key())));
                }
                groups.add(new Group(game, ordered));
            }
        });
        return List.copyOf(groups);
    }

    public static Sheet sheet(List<Group> groups, String onlyGame, int pageSize, int gameListAbove, int page) {
        int size = Math.max(1, pageSize);
        if (onlyGame == null && groups.size() > gameListAbove) {
            int pages = Math.max(1, (groups.size() + size - 1) / size);
            int current = Math.clamp(page, 1, pages);
            int from = (current - 1) * size;
            return new Sheet(Layout.GAMES, groups.subList(from, Math.min(groups.size(), from + size)), current,
                    pages);
        }
        int lines = groups.stream().mapToInt(group -> group.stats().size()).sum();
        int pages = Math.max(1, (lines + size - 1) / size);
        int current = Math.clamp(page, 1, pages);
        int skip = (current - 1) * size;
        int left = size;
        List<Group> shown = new ArrayList<>();
        for (Group group : groups) {
            if (left <= 0) {
                break;
            }
            int count = group.stats().size();
            if (skip >= count) {
                skip -= count;
                continue;
            }
            int end = Math.min(count, skip + left);
            shown.add(new Group(group.game(), group.stats().subList(skip, end)));
            left -= end - skip;
            skip = 0;
        }
        return new Sheet(Layout.STATS, shown, current, pages);
    }

    public static List<StatDefinition> profile(PlayerStats stats, List<StatDefinition> configured, int limit) {
        return configured.stream()
                .filter(StatDefinition::inProfile)
                .filter(definition -> stats.has(definition.key()))
                .limit(Math.max(0, limit))
                .toList();
    }
}
