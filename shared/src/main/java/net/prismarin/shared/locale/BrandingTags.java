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

    public static TextFormatter formatter(Branding branding) {
        return new TextFormatter(resolver(branding));
    }

    public static TagResolver resolver(Branding branding) {
        String prefix = LegacyColors.toMiniMessage(branding.prefix())
                .replace("%brand%", branding.title())
                .replace("<brand>", branding.title());
        return TagResolver.resolver(
                TagResolver.resolver("brand", Tag.preProcessParsed(branding.title())),
                TagResolver.resolver("brand_prefix", Tag.preProcessParsed(prefix)),
                TagResolver.resolver("brand_primary", Tag.styling(TextColor.fromHexString(branding.primaryColor()))),
                TagResolver.resolver("brand_accent", Tag.styling(TextColor.fromHexString(branding.accentColor()))));
    }
}
