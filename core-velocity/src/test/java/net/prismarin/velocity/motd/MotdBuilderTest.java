package net.prismarin.velocity.motd;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.brand.Branding;
import net.prismarin.shared.locale.BrandingTags;
import net.prismarin.velocity.config.ProxyConfig;
import net.prismarin.velocity.config.ProxyConfigCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotdBuilderTest {

    private final MotdBuilder builder = new MotdBuilder();

    @Test
    void rendersTwoLineDescription() {
        String plain = plain(config(false));
        assertEquals("PRISMARIN\nLobby · Creative · Minigame — 1.8 bis 1.21", plain);
    }

    @Test
    void rendersMaintenanceVariantWhenActive() {
        String plain = plain(config(true));
        assertEquals("PRISMARIN — Wartung\nBald zurück.", plain);
    }

    @Test
    void rendersDefaultConfigWithBrandTag() {
        MotdBuilder branded = new MotdBuilder(BrandingTags.formatter(Branding.DEFAULT));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(branded.description(ProxyConfigCodec.defaults()));
        assertEquals("PRISMARIN\nLobby, Spiele und mehr", plain);
    }

    private String plain(ProxyConfig config) {
        return PlainTextComponentSerializer.plainText().serialize(builder.description(config));
    }

    private ProxyConfig config(boolean maintenance) {
        return new ProxyConfig(
                "<gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient>",
                "<gray>Lobby · Creative · Minigame</gray> <dark_gray>—</dark_gray> <gray>1.8 bis 1.21</gray>",
                "<gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient> <dark_gray>—</dark_gray> <color:#FBBF24><b>Wartung</b></color>",
                "<gray>Bald zurück.</gray>",
                "Wartung", maintenance, 200, "lobby");
    }
}
