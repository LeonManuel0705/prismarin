package net.prismarin.core.nick;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

final class PlayerNameIndex {

    private static final String FIELD = "playersByName";

    private final Map<String, Object> names;

    private PlayerNameIndex(Map<String, Object> names) {
        this.names = names;
    }

    @SuppressWarnings("unchecked")
    static PlayerNameIndex locate(Server server, Logger logger) {
        try {
            Object playerList = server.getClass().getMethod("getHandle").invoke(server);
            for (Class<?> type = playerList.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(FIELD);
                    field.setAccessible(true);
                    if (field.get(playerList) instanceof Map<?, ?> map) {
                        return new PlayerNameIndex((Map<String, Object>) map);
                    }
                } catch (NoSuchFieldException ignored) {
                    continue;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.FINE, "Paper's player name index is not reachable", e);
        }
        logger.warning("Paper's player name index is not reachable: players nicked after joining keep their "
                + "real name for /msg and getPlayerExact until they rejoin");
        return new PlayerNameIndex(null);
    }

    boolean available() {
        return names != null;
    }

    void rename(Player player, String previousName, String currentName) {
        if (names == null || previousName.equalsIgnoreCase(currentName)) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            String previousKey = previousName.toLowerCase(Locale.ROOT);
            String currentKey = currentName.toLowerCase(Locale.ROOT);
            if (names.get(previousKey) == handle) {
                names.remove(previousKey);
            }
            Object holder = names.get(currentKey);
            if (holder == null || holder == handle) {
                names.put(currentKey, handle);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("could not update the player name index", e);
        }
    }
}
