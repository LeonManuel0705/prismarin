package net.prismarin.core.locale;

import java.util.Locale;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import org.bukkit.entity.Player;

public final class PlayerLocales {

    private PlayerLocales() {
        throw new AssertionError();
    }

    public static Locale of(Player player, ProfileService profiles) {
        return profiles.getCached(player.getUniqueId())
                .filter(profile -> Boolean.parseBoolean(profile.getSettings().get(PlayerProfile.SETTING_LOCALE_OVERRIDE)))
                .map(profile -> Locale.forLanguageTag(profile.getLocale()))
                .orElseGet(player::locale);
    }
}
