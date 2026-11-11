package net.prismarin.core.api;

import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface ChatDecorator {

    default Component prefix(Player source, Audience viewer) {
        return Component.empty();
    }

    default Component suffix(Player source, Audience viewer) {
        return Component.empty();
    }

    default Component message(Player source, Audience viewer, Component message) {
        return message;
    }

    default Map<String, Component> placeholders(Player source, Audience viewer) {
        return Map.of();
    }
}
