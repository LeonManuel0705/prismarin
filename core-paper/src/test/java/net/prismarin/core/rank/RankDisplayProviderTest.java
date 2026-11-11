package net.prismarin.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.core.config.RankDisplaySettings;
import net.prismarin.shared.text.TextFormatter;
import org.junit.jupiter.api.Test;

class RankDisplayProviderTest {

    private static final TextFormatter FORMATTER = TextFormatter.plain();

    private static RankDisplayProvider provider(String beforeName, String alone) {
        return new RankDisplayProvider(null, new RankDisplaySettings(beforeName, alone,
                RankDisplaySettings.DEFAULT.trim(), "rank-label"), FORMATTER);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void anExistingPrefixWithSeparatorKeepsItBeforeTheNameAndDropsItAlone() {
        RankDisplayProvider display = provider("%prefix%", "%rank%");
        Component prefix = FORMATTER.format("&cAdmin &8┃");
        assertEquals("Admin ┃", plain(display.render("%prefix%", prefix, prefix)));
        assertEquals("Admin", plain(display.render("%rank%", prefix, prefix)));
    }

    @Test
    void plainRanksGetTheSeparatorFromTheTemplate() {
        RankDisplayProvider display = provider("%rank% <dark_gray>┃</dark_gray>", "%rank%");
        Component prefix = FORMATTER.format("&6Premium");
        assertEquals("Premium ┃", plain(display.render("%rank% <dark_gray>┃</dark_gray>", prefix, prefix)));
        Component already = FORMATTER.format("&cAdmin &8┃ ");
        assertEquals("Admin ┃", plain(display.render("%rank% <dark_gray>┃</dark_gray>", already, already)));
    }

    @Test
    void anExplicitLabelWinsForTheRankAlone() {
        RankDisplayProvider display = provider("%prefix%", "%rank%");
        assertEquals("Team", plain(display.render("%rank%", FORMATTER.format("&c✦ &8|"),
                FORMATTER.format("&cTeam"))));
    }

    @Test
    void noRankStaysEmptyInsteadOfALonelySeparator() {
        RankDisplayProvider display = provider("%rank% <dark_gray>┃</dark_gray>", "%rank%");
        assertEquals("", plain(display.render("%rank% <dark_gray>┃</dark_gray>", Component.empty(), null)));
    }
}
