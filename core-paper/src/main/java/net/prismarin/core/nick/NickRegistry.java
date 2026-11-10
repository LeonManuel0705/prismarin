package net.prismarin.core.nick;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public final class NickRegistry {

    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

    public boolean nicked(UUID player) {
        return shown.containsKey(player);
    }

    public Optional<Shown> shown(UUID player) {
        return Optional.ofNullable(shown.get(player));
    }

    public Optional<UUID> byNick(String nick) {
        if (nick == null) {
            return Optional.empty();
        }
        return shown.entrySet().stream()
                .filter(entry -> entry.getValue().nick().equalsIgnoreCase(nick))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Map<UUID, Shown> all() {
        return Map.copyOf(shown);
    }

    void put(UUID player, Shown nick) {
        shown.put(player, nick);
    }

    void remove(UUID player) {
        shown.remove(player);
    }

    public record Shown(String nick, String realName, String skin) {
    }
}
