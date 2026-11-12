package net.prismarin.core.reward;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticApi;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.reward.Reward;
import net.prismarin.core.command.CurrencyNames;
import net.prismarin.core.cosmetics.CosmeticReward;
import net.prismarin.core.cosmetics.CosmeticTexts;
import net.prismarin.shared.config.CoreConfig;

@Singleton
public final class RewardSummary {

    private final LocaleService locale;
    private final EconomyService economy;
    private final CoreConfig config;
    private final Provider<CosmeticTexts> cosmeticTexts;

    @Inject
    public RewardSummary(LocaleService locale, EconomyService economy, CoreConfig config,
                         Provider<CosmeticTexts> cosmeticTexts) {
        this.locale = locale;
        this.economy = economy;
        this.config = config;
        this.cosmeticTexts = cosmeticTexts;
    }

    public List<Component> parts(Reward reward, Locale language) {
        List<Component> parts = new ArrayList<>();
        if (economy.enabled()) {
            for (Map.Entry<String, Long> entry : reward.currencies().entrySet()) {
                Optional<Currency> currency = economy.currency(entry.getKey());
                currency.ifPresent(found -> parts.add(locale.render("reward.currency", language,
                        Placeholder.unparsed("amount", String.format(language, "%,d", entry.getValue())),
                        Placeholder.component("currency", CurrencyNames.render(locale, found, language)))));
            }
        }
        if (config.systemEnabled(PrismarinSystems.LEVELS) && reward.xp() > 0L) {
            parts.add(locale.render("reward.xp", language,
                    Placeholder.unparsed("amount", String.format(language, "%,d", reward.xp()))));
        }
        if (config.systemEnabled(PrismarinSystems.LEVELS) && reward.levels() > 0) {
            parts.add(locale.render(reward.levels() == 1 ? "reward.level" : "reward.levels", language,
                    Placeholder.unparsed("amount", Integer.toString(reward.levels()))));
        }
        boolean extra = !reward.commands().isEmpty();
        for (Map.Entry<String, String> custom : reward.custom().entrySet()) {
            Optional<Component> cosmetic = CosmeticReward.ID.equals(custom.getKey())
                    ? cosmetic(custom.getValue(), language) : Optional.empty();
            if (cosmetic.isPresent()) {
                parts.add(locale.render("reward.cosmetic", language, Placeholder.component("cosmetic",
                        cosmetic.get())));
            } else {
                extra = true;
            }
        }
        if (extra) {
            parts.add(locale.render("reward.extra", language));
        }
        return List.copyOf(parts);
    }

    private Optional<Component> cosmetic(String id, Locale language) {
        Optional<CosmeticService> service = CosmeticApi.service();
        Optional<Cosmetic> cosmetic = service.flatMap(found -> found.cosmetic(id.strip().toLowerCase(Locale.ROOT)));
        if (cosmetic.isEmpty()) {
            return Optional.empty();
        }
        CosmeticRarity rarity = service.get().rarity(cosmetic.get().rarity())
                .orElseGet(() -> CosmeticRarity.of(CosmeticRarity.COMMON, "Common", 0xFFFFFF, 0));
        return Optional.of(cosmeticTexts.get().name(cosmetic.get(), rarity, language, null));
    }

    public List<Component> lines(Reward reward, Locale language) {
        return parts(reward, language).stream()
                .map(part -> locale.render("reward.line", language, Placeholder.component("reward", part)))
                .toList();
    }
}
