package net.prismarin.velocity.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.velocity.config.ProxyConfig;

public final class MotdBuilder {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component description(ProxyConfig config) {
        if (config.maintenance()) {
            return lines(config.maintenanceMotdLine1(), config.maintenanceMotdLine2());
        }
        return lines(config.motdLine1(), config.motdLine2());
    }

    private Component lines(String line1, String line2) {
        return miniMessage.deserialize(line1)
                .append(Component.newline())
                .append(miniMessage.deserialize(line2));
    }
}
