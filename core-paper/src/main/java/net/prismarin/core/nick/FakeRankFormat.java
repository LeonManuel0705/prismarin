package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.text.PrefixStyle;
import org.bukkit.entity.Player;

@Singleton
public final class FakeRankFormat {

    private static final long LOOK_CACHE_MILLIS = 10_000L;
    private static final String DEFAULT_PREFIX_KEY = "rank.default-prefix";

    private final NickSettings settings;
    private final NickPermissions permissions;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final Map<String, Component> prefixCache = new ConcurrentHashMap<>();
    private volatile FakeRankResolver.Look look;
    private volatile long lookExpires;

    @Inject
    public FakeRankFormat(NickSettings settings, NickPermissions permissions, LocaleService locale,
                          ProfileService profiles) {
        this.settings = settings;
        this.permissions = permissions;
        this.locale = locale;
        this.profiles = profiles;
    }

    public Component prefix(Player player) {
        return look().prefix()
                .map(prefix -> prefixCache.computeIfAbsent(prefix, text -> locale.format(text)))
                .orElseGet(() -> locale.render(DEFAULT_PREFIX_KEY, PlayerLocales.of(player, profiles)));
    }

    public int weight() {
        return look().weight();
    }

    public NamedTextColor nameColor() {
        return look().nameColor();
    }

    public TextColor chatColor() {
        return look().prefix().flatMap(PrefixStyle::lastColor).orElse(null);
    }

    public boolean hasPerk(String permission) {
        NickSettings.FakeRank rank = settings.fakeRank();
        return rank.perks().contains(permission) || permissions.groupHas(rank.group(), permission);
    }

    public FakeRankResolver.Look look() {
        long now = System.currentTimeMillis();
        FakeRankResolver.Look current = look;
        if (current != null && now < lookExpires) {
            return current;
        }
        FakeRankResolver.Look resolved = FakeRankResolver.resolve(settings.fakeRank(),
                permissions.group(settings.fakeRank().group()));
        look = resolved;
        lookExpires = now + LOOK_CACHE_MILLIS;
        return resolved;
    }
}
