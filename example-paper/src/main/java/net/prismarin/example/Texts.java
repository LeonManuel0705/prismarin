package net.prismarin.example;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.player.PlayerService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class Texts {

    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final ClassLoader loader;
    private final PlayerService players;

    Texts(ClassLoader loader, PlayerService players) {
        this.loader = loader;
        this.players = players;
    }

    void send(CommandSender receiver, String key, String... pairs) {
        receiver.sendMessage(render(locale(receiver), key, pairs));
    }

    Component render(Locale locale, String key, String... pairs) {
        TagResolver[] resolvers = new TagResolver[pairs.length / 2];
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            resolvers[index / 2] = Placeholder.unparsed(pairs[index], pairs[index + 1]);
        }
        String template = ResourceBundle.getBundle("example", locale, loader, NO_FALLBACK).getString(key);
        return MiniMessage.miniMessage().deserialize(template, resolvers);
    }

    Locale locale(CommandSender receiver) {
        return receiver instanceof Player player ? locale(player.getUniqueId()) : Locale.ENGLISH;
    }

    Locale locale(UUID player) {
        return players.locale(player);
    }
}
