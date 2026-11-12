package net.prismarin.shared.chatfilter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;

public final class ChatState {

    private static final int MAX_LENGTH = 256;
    private static final int MAX_ATTEMPTS = 64;

    private record Line(long at, String text) {
    }

    private final Deque<Long> attempts = new ArrayDeque<>();
    private final Deque<Line> recent = new ArrayDeque<>();
    private volatile long lastActivity;

    public synchronized boolean flooding(long now, int maxMessages, long windowMillis) {
        lastActivity = now;
        while (!attempts.isEmpty() && attempts.peekFirst() <= now - windowMillis) {
            attempts.pollFirst();
        }
        boolean over = attempts.size() >= Math.max(1, maxMessages);
        attempts.addLast(now);
        while (attempts.size() > MAX_ATTEMPTS) {
            attempts.pollFirst();
        }
        return over;
    }

    public synchronized OptionalInt repeated(String simplified, long now, long windowMillis, int similarity,
                                             int minLength) {
        lastActivity = now;
        while (!recent.isEmpty() && recent.peekFirst().at() <= now - windowMillis) {
            recent.pollFirst();
        }
        String text = clip(simplified);
        if (text.length() < Math.max(1, minLength)) {
            return OptionalInt.empty();
        }
        int best = -1;
        for (Line line : recent) {
            int score = similarity(text, line.text());
            if (score >= similarity && score > best) {
                best = score;
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    public synchronized void remember(String simplified, long now, int history) {
        lastActivity = now;
        String text = clip(simplified);
        if (text.isEmpty()) {
            return;
        }
        recent.addLast(new Line(now, text));
        while (recent.size() > Math.max(1, history)) {
            recent.pollFirst();
        }
    }

    public long lastActivity() {
        return lastActivity;
    }

    public static int similarity(String first, String second) {
        if (first.equals(second)) {
            return 100;
        }
        int longest = Math.max(first.length(), second.length());
        if (longest == 0) {
            return 100;
        }
        return (int) Math.round((1.0 - (double) distance(first, second) / longest) * 100.0);
    }

    static int distance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                int cost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private static String clip(String text) {
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }
}
