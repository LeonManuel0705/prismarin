package net.prismarin.shared.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatOrder;

public final class TopList {

    public record Row(UUID player, String name, long value, long at) {

        public Row {
            Objects.requireNonNull(player, "player");
            name = name == null ? "" : name;
        }
    }

    private TopList() {
        throw new AssertionError();
    }

    public static Comparator<Row> comparator(StatOrder order) {
        return (first, second) -> {
            int byValue = order.compare(first.value(), second.value());
            if (byValue != 0) {
                return byValue;
            }
            int byTime = Long.compare(first.at(), second.at());
            return byTime != 0 ? byTime : first.player().compareTo(second.player());
        };
    }

    public static List<Row> sort(Collection<Row> rows, StatOrder order) {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(comparator(order));
        return List.copyOf(sorted);
    }

    public static List<Integer> ranks(List<Row> sorted, boolean shared) {
        List<Integer> ranks = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            boolean tie = shared && index > 0 && sorted.get(index).value() == sorted.get(index - 1).value();
            ranks.add(tie ? ranks.get(index - 1) : index + 1);
        }
        return List.copyOf(ranks);
    }

    public static List<LeaderboardEntry> entries(List<Row> sorted, int size, boolean shared, Map<UUID, String> names) {
        int count = Math.min(Math.max(0, size), sorted.size());
        List<Integer> ranks = ranks(sorted.subList(0, count), shared);
        List<LeaderboardEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Row row = sorted.get(index);
            String shown = names.getOrDefault(row.player(), row.name());
            if (shown.isBlank()) {
                shown = row.player().toString().substring(0, 8);
            }
            entries.add(new LeaderboardEntry(ranks.get(index), row.player(), shown, row.value()));
        }
        return List.copyOf(entries);
    }

    public static long[] values(Collection<Row> rows) {
        long[] values = new long[rows.size()];
        int index = 0;
        for (Row row : rows) {
            values[index++] = row.value();
        }
        Arrays.sort(values);
        return values;
    }

    public static int rank(long[] ascending, long value, StatOrder order) {
        int better = order == StatOrder.HIGHEST
                ? ascending.length - upperBound(ascending, value)
                : lowerBound(ascending, value);
        return better + 1;
    }

    private static int lowerBound(long[] ascending, long value) {
        int low = 0;
        int high = ascending.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (ascending[middle] < value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperBound(long[] ascending, long value) {
        int low = 0;
        int high = ascending.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (ascending[middle] <= value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
