package net.prismarin.lobby.action;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.util.ConfigText;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.entity.Player;

@Singleton
public final class ActionRunner {

    private final LobbyPlugin plugin;
    private final ProfileService profiles;
    private final TextFormatter formatter;
    private final ConfigText texts;
    private final LobbyMenus menus;
    private final LobbyTravel travel;
    private final LobbyDirectory lobbies;

    @Inject
    public ActionRunner(LobbyPlugin plugin, ProfileService profiles, TextFormatter formatter, ConfigText texts,
                        LobbyMenus menus, LobbyTravel travel, LobbyDirectory lobbies) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.formatter = formatter;
        this.texts = texts;
        this.menus = menus;
        this.travel = travel;
        this.lobbies = lobbies;
    }

    public void run(Player player, List<ItemAction> actions) {
        for (ItemAction action : actions) {
            if (!player.isOnline()) {
                return;
            }
            run(player, action);
        }
    }

    private void run(Player player, ItemAction action) {
        switch (action.type()) {
            case PLAYER -> player.performCommand(expand(player, action.argument()));
            case CONSOLE -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    expand(player, action.argument()));
            case MESSAGE -> player.sendMessage(text(player, action.argument()));
            case ACTIONBAR -> player.sendActionBar(text(player, action.argument()));
            case MENU -> {
                if (!menus.open(player, action.argument())) {
                    travel.deny(player);
                }
            }
            case SERVER -> travel.connect(player, action.argument());
            case GROUP -> travel.playGroup(player, action.argument());
            case LOBBY -> lobbies.worldLobby(action.argument()).ifPresentOrElse(
                    lobby -> travel.travel(player, new WorldTarget(lobby)),
                    () -> {
                        travel.deny(player);
                        plugin.getLogger().warning("Custom item action 'lobby: " + action.argument()
                                + "' points to no enabled world lobby");
                    });
            case SOUND -> {
                SoundSpec spec = SoundSpec.parse(action.argument());
                player.playSound(Sound.sound(Key.key(spec.key()), Sound.Source.MASTER, spec.volume(), spec.pitch()),
                        Sound.Emitter.self());
            }
        }
    }

    private String expand(Player player, String command) {
        String replaced = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName());
        return formatter.expandExternal(player.getUniqueId(), replaced);
    }

    private Component text(Player player, String raw) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        return texts.render(raw, playerLocale, player.getUniqueId(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("world", player.getWorld().getName()));
    }
}
