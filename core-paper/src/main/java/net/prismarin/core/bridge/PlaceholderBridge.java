package net.prismarin.core.bridge;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.prismarin.api.Prismarin;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.player.PlayerApi;
import net.prismarin.shared.text.TextFormatter;

public final class PlaceholderBridge {

    private final PrismarinExpansion expansion;
    private final TextFormatter formatter;

    private PlaceholderBridge(PrismarinExpansion expansion, TextFormatter formatter) {
        this.expansion = expansion;
        this.formatter = formatter;
    }

    public static PlaceholderBridge enable(Prismarin prismarin, NickService nicks, TextFormatter formatter,
                                           String version) {
        PrismarinExpansion expansion = new PrismarinExpansion(new PrismarinPlaceholders(prismarin, nicks,
                PlayerApi.service().orElse(null), LegacyComponentSerializer.legacySection()::serialize), version);
        expansion.register();
        formatter.expander(new PlaceholderApiExpander());
        return new PlaceholderBridge(expansion, formatter);
    }

    public void disable() {
        formatter.expander(null);
        expansion.unregister();
    }
}
