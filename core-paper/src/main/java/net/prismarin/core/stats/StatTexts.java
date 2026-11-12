package net.prismarin.core.stats;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.core.locale.ConfiguredText;

@Singleton
public final class StatTexts {

    static final List<String> PALETTE = List.of("#22D3EE", "#F472B6", "#FBBF24", "#34D399", "#A78BFA", "#FB7185",
            "#60A5FA", "#A3E635", "#FB923C", "#2DD4BF");

    private final LocaleService locale;
    private final ConfiguredText text;
    private final StatsSettings settings;
    private final PaperStats stats;

    @Inject
    public StatTexts(LocaleService locale, ConfiguredText text, StatsSettings settings, PaperStats stats) {
        this.locale = locale;
        this.text = text;
        this.settings = settings;
        this.stats = stats;
    }

    public Component name(StatDefinition definition, Locale target) {
        return text.render(definition.name(), target, null);
    }

    public StatGame meta(String game) {
        if (!StatKeys.validGame(game)) {
            return StatGame.of(StatKeys.GENERAL);
        }
        return stats.service().map(service -> service.game(game))
                .orElseGet(() -> settings.game(StatKeys.gameId(game)).map(config -> config.apply(null))
                        .orElseGet(() -> StatGame.of(game)));
    }

    public Component game(String game, Locale target) {
        if (!StatKeys.validGame(game)) {
            return Component.text(capitalize(game)).color(TextColor.fromHexString(PALETTE.getFirst()));
        }
        StatGame meta = meta(game);
        return text.render(meta.name(), target, null).colorIfAbsent(color(meta));
    }

    public Component period(StatPeriod period, Locale target) {
        return locale.render("stats.period." + period.id(), target);
    }

    public static TextColor color(StatGame game) {
        if (game.hasColor()) {
            TextColor chosen = game.color().startsWith("#") ? TextColor.fromHexString(game.color())
                    : NamedTextColor.NAMES.value(game.color());
            if (chosen != null) {
                return chosen;
            }
        }
        return TextColor.fromHexString(PALETTE.get(Math.floorMod(game.id().hashCode(), PALETTE.size())));
    }

    static String capitalize(String game) {
        if (game == null || game.isEmpty()) {
            return StatKeys.GENERAL;
        }
        return Character.toUpperCase(game.charAt(0)) + game.substring(1);
    }
}
