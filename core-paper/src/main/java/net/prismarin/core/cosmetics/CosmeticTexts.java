package net.prismarin.core.cosmetics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.command.CurrencyNames;
import net.prismarin.core.locale.ConfiguredText;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Singleton
public final class CosmeticTexts {

    private final LocaleService locale;
    private final ConfiguredText texts;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public CosmeticTexts(LocaleService locale, ConfiguredText texts, ProfileService profiles, CoreConfig config) {
        this.locale = locale;
        this.texts = texts;
        this.profiles = profiles;
        this.config = config;
    }

    public Locale language(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    public Component render(CommandSender sender, String key, TagResolver... resolvers) {
        return locale.render(key, language(sender), resolvers);
    }

    public Component render(Locale target, String key, TagResolver... resolvers) {
        return locale.render(key, target, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(sender, key, resolvers));
    }

    public Component text(NoticeText text, Locale target, UUID viewer) {
        return texts.render(text, target, viewer);
    }

    public Component name(Cosmetic cosmetic, CosmeticRarity rarity, Locale target, UUID viewer) {
        return text(cosmetic.name(), target, viewer).colorIfAbsent(TextColor.color(rarity.color()));
    }

    public Component rarity(CosmeticRarity rarity, Locale target) {
        return text(rarity.name(), target, null).colorIfAbsent(TextColor.color(rarity.color()));
    }

    public Component category(CosmeticCategory category, Locale target) {
        return text(category.name(), target, null);
    }

    public Component price(CosmeticPrice price, Locale target) {
        return currency("cosmetics.price", price.currency(), price.amount(), target);
    }

    public Component currency(String prefix, String currency, long amount, Locale target) {
        String key = prefix + "." + currency;
        if (locale.raw(key, target).equals(key)) {
            key = prefix + ".currency";
        }
        String value = String.format(target, "%,d", amount);
        return locale.render(key, target, Placeholder.unparsed("amount", value),
                Placeholder.component("currency", CurrencyNames.render(locale, Currency.of(currency), target)));
    }

    public TagResolver cosmetic(Component name) {
        return Placeholder.component("cosmetic", name);
    }
}
