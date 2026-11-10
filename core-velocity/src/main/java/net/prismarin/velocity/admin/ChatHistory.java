package net.prismarin.velocity.admin;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ChatHistory {

    public record Line(long at, String server, UUID player, String name, String message) {
    }

    public record Evidence(String server, List<Line> lines) {
    }

    private static final int MAX_PER_SERVER = 1_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, Deque<Line>> servers = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long retainMillis;

    public ChatHistory(Clock clock, long retainMillis) {
        this.clock = clock;
        this.retainMillis = retainMillis;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(connection -> record(connection.getServerInfo().getName(),
                player.getUniqueId(), player.getUsername(), event.getMessage()));
    }

    public void record(String server, UUID player, String name, String message) {
        Deque<Line> lines = servers.computeIfAbsent(key(server), ignored -> new ConcurrentLinkedDeque<>());
        long now = clock.millis();
        lines.addLast(new Line(now, server, player, name, message));
        prune(lines, now);
    }

    public List<Line> recent(String server, UUID focus, boolean onlyFocus, long windowMillis, int maxLines) {
        Deque<Line> lines = servers.get(key(server));
        if (lines == null || maxLines <= 0) {
            return List.of();
        }
        long since = clock.millis() - windowMillis;
        List<Line> matching = new ArrayList<>();
        for (Line line : lines) {
            if (line.at() >= since && (!onlyFocus || line.player().equals(focus))) {
                matching.add(line);
            }
        }
        if (matching.stream().noneMatch(line -> line.player().equals(focus))) {
            return List.of();
        }
        return List.copyOf(matching.subList(Math.max(0, matching.size() - maxLines), matching.size()));
    }

    public Evidence lastSeen(UUID focus, boolean onlyFocus, long windowMillis, int maxLines) {
        long since = clock.millis() - windowMillis;
        String server = null;
        long latest = Long.MIN_VALUE;
        for (Deque<Line> lines : servers.values()) {
            for (Line line : lines) {
                if (line.player().equals(focus) && line.at() >= since && line.at() > latest) {
                    latest = line.at();
                    server = line.server();
                }
            }
        }
        if (server == null) {
            return new Evidence("", List.of());
        }
        return new Evidence(server, recent(server, focus, onlyFocus, windowMillis, maxLines));
    }

    public static String format(Line line, ZoneId zone) {
        return TIME.format(Instant.ofEpochMilli(line.at()).atZone(zone)) + " " + line.name() + ": " + line.message();
    }

    private void prune(Deque<Line> lines, long now) {
        Iterator<Line> iterator = lines.iterator();
        while (iterator.hasNext()) {
            Line line = iterator.next();
            if (now - line.at() <= retainMillis && lines.size() <= MAX_PER_SERVER) {
                break;
            }
            iterator.remove();
        }
    }

    private static String key(String server) {
        return server.toLowerCase(Locale.ROOT);
    }
}
