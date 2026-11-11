package net.prismarin.shared.locale;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.brand.Branding;
import net.prismarin.shared.text.LegacyColors;
import net.prismarin.shared.text.TextFormatter;

public final class BrandingTags {

    private BrandingTags() {
        throw new AssertionError();
    }

    private static String brand(String raw, String title) {
        return LegacyColors.toMiniMessage(raw).replace("%brand%", title).replace("<brand>", title);
    }

    public static TextFormatter formatter(Branding branding) {
        return new TextFormatter(resolver(branding));
    }

    public static TagResolver resolver(Branding branding) {
        String prefix = brand(branding.prefix(), branding.title());
        return TagResolver.resolver(
                TagResolver.resolver("brand", Tag.preProcessParsed(branding.title())),
                TagResolver.resolver("brand_prefix", Tag.preProcessParsed(prefix)),
                TagResolver.resolver("success_prefix", Tag.preProcessParsed(
                        brand(branding.successPrefix(), branding.title()).replace("%brand_prefix%", prefix)
                                .replace("<brand_prefix>", prefix))),
                TagResolver.resolver("error_prefix", Tag.preProcessParsed(
                        brand(branding.errorPrefix(), branding.title()).replace("%brand_prefix%", prefix)
                                .replace("<brand_prefix>", prefix))),
                TagResolver.resolver("brand_primary", Tag.styling(TextColor.fromHexString(branding.primaryColor()))),
                TagResolver.resolver("brand_accent", Tag.styling(TextColor.fromHexString(branding.accentColor()))));
    }
}
