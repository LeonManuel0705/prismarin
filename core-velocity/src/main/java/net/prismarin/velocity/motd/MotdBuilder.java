package net.prismarin.velocity.motd;

import net.kyori.adventure.text.Component;
import net.prismarin.shared.text.TextFormatter;
import net.prismarin.velocity.config.ProxyConfig;

public final class MotdBuilder {

    private final TextFormatter formatter;

    public MotdBuilder() {
        this(TextFormatter.plain());
    }

    public MotdBuilder(TextFormatter formatter) {
        this.formatter = formatter;
    }

    public Component description(ProxyConfig config) {
        if (config.maintenance()) {
            return lines(config.maintenanceMotdLine1(), config.maintenanceMotdLine2());
        }
        return lines(config.motdLine1(), config.motdLine2());
    }

    private Component lines(String line1, String line2) {
        return formatter.format(line1)
                .append(Component.newline())
                .append(formatter.format(line2));
    }
}
