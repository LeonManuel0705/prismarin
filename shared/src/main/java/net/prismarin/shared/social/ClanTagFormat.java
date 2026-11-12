package net.prismarin.shared.social;

import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.social.Clan;
import net.prismarin.shared.text.TextFormatter;

public record ClanTagFormat(String format, Position position, boolean chat, boolean tablist) {

    public static final String DEFAULT_FORMAT = "&7[&f%name%&7]";
    public static final ClanTagFormat DEFAULT = new ClanTagFormat(DEFAULT_FORMAT, Position.AFTER, true, true);

    public enum Position {
        BEFORE,
        AFTER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Position> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            return switch (id.strip().toLowerCase(Locale.ROOT)) {
                case "before", "prefix", "front", "vor", "vorne", "davor" -> Optional.of(BEFORE);
                case "after", "suffix", "behind", "nach", "hinten", "dahinter" -> Optional.of(AFTER);
                default -> Optional.empty();
            };
        }
    }

    public ClanTagFormat {
        format = format == null || format.isBlank() ? DEFAULT_FORMAT : format;
        position = position == null ? Position.AFTER : position;
    }

    public boolean before() {
        return position == Position.BEFORE;
    }

    public Component render(TextFormatter formatter, Clan clan) {
        return render(formatter, clan.tag(), clan.name(), clan.color());
    }

    public Component render(TextFormatter formatter, String tag, String clanName, String color) {
        Component shown = ClanColors.color(color).map(found -> Component.text(tag, found))
                .orElseGet(() -> Component.text(tag));
        return formatter.format(format, Placeholder.component("name", shown), Placeholder.component("tag", shown),
                Placeholder.unparsed("clan", clanName == null ? "" : clanName));
    }
}
