package net.prismarin.lobby.player;

import java.util.Locale;
import net.prismarin.api.profile.ProfileService;
import org.bukkit.entity.Player;

public final class PlayerLocales {

    private PlayerLocales() {
        throw new AssertionError();
    }

    public static Locale of(Player player, ProfileService profiles) {
        return profiles.getCached(player.getUniqueId())
                .map(profile -> Locale.forLanguageTag(profile.getLocale()))
                .orElseGet(player::locale);
    }
}
