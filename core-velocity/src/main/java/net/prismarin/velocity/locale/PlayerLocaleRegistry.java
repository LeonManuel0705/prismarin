package net.prismarin.velocity.locale;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerLocaleRegistry {

    private record Entry(Locale locale, String server) {
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface ChangeListener {

        void changed(UUID player, Locale previous, Locale current);
    }

    public void onChange(ChangeListener listener) {
        listeners.add(listener);
    }

    public void update(UUID player, String languageTag, String server) {
        if (languageTag == null || languageTag.isBlank()) {
            return;
        }
        Entry current = new Entry(Locale.forLanguageTag(languageTag.replace('_', '-')), server);
        Entry previous = entries.put(player, current);
        if (previous != null && !previous.locale().equals(current.locale())) {
            listeners.forEach(listener -> listener.changed(player, previous.locale(), current.locale()));
        }
    }

    public Optional<Locale> locale(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(player)).map(Entry::locale);
    }

    public boolean prismarinBackend(Player player) {
        Entry entry = entries.get(player.getUniqueId());
        return entry != null && player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(entry.server()))
                .orElse(false);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        entries.remove(event.getPlayer().getUniqueId());
    }
}
