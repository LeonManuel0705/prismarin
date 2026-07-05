package net.prismarin.velocity.locale;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;

public final class ProxyMessages {

    private final LocaleService locale;
    private final Locale fallback;

    public ProxyMessages(LocaleService locale, Locale fallback) {
        this.locale = locale;
        this.fallback = fallback;
    }

    public Component render(CommandSource source, String key, TagResolver... resolvers) {
        return locale.render(key, localeOf(source), resolvers);
    }

    public void send(CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(render(source, key, resolvers));
    }

    public Locale localeOf(CommandSource source) {
        if (source instanceof Player player) {
            Locale effective = player.getEffectiveLocale();
            if (effective != null) {
                return effective;
            }
        }
        return fallback;
    }
}
