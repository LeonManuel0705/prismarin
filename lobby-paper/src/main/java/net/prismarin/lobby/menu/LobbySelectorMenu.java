package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.config.WorldLobby;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.network.LobbyTarget;
import net.prismarin.lobby.network.LobbyTarget.ServerTarget;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.util.ConfigText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LobbySelectorMenu {

    private static final int PER_ROW = 5;
    private static final int WIDE_PER_ROW = 7;
    private static final int MAX_CONTENT_ROWS = 4;

    private final MenuService menuService;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyDirectory lobbies;
    private final LobbyTravel travel;
    private final MenuKit kit;

    @Inject
    public LobbySelectorMenu(MenuService menuService, LocaleService locale, ProfileService profiles,
                             LobbyDirectory lobbies, LobbyTravel travel, MenuKit kit) {
        this.menuService = menuService;
        this.locale = locale;
        this.profiles = profiles;
        this.lobbies = lobbies;
        this.travel = travel;
        this.kit = kit;
    }

    public void open(Player player, Consumer<Player> back) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        List<LobbyTarget> targets = lobbies.selectorTargets();
        int perRow = targets.size() > PER_ROW * MAX_CONTENT_ROWS ? WIDE_PER_ROW : PER_ROW;
        int count = Math.min(targets.size(), perRow * MAX_CONTENT_ROWS);
        int contentRows = MenuLayout.rowsFor(count, perRow);
        Menu menu = new Menu(locale.render("lobby.selector.title", playerLocale), contentRows + 2);
        kit.fill(menu);
        if (count == 0) {
            menu.button(13, kit.decoration(ItemBuilder.of(Material.BARRIER)
                    .name(locale.render("lobby.selector.empty", playerLocale))
                    .build()));
        } else {
            List<Integer> slots = MenuLayout.centeredSlots(1, count, perRow);
            for (int i = 0; i < count; i++) {
                menu.button(slots.get(i), button(player, playerLocale, targets.get(i)));
            }
        }
        int bottom = (contentRows + 1) * 9;
        if (back != null) {
            menu.button(bottom, kit.back(playerLocale, back));
        }
        menu.button(bottom + 8, kit.close(playerLocale));
        menuService.open(player, menu);
    }

    private MenuButton button(Player player, Locale playerLocale, LobbyTarget target) {
        boolean current = lobbies.isCurrent(player, target);
        boolean permitted = lobbies.permitted(player, target);
        List<Component> lore = new ArrayList<>();
        boolean reachable = true;
        Material fallback = target.premium() ? Material.GOLD_BLOCK : Material.PRISMARINE;
        String iconName = "";
        switch (target) {
            case WorldTarget world -> {
                WorldLobby lobby = world.lobby();
                iconName = lobby.icon();
                lore.add(players(playerLocale, lobbies.visiblePlayers(lobby, player), lobby.softMaxPlayers()));
            }
            case ServerTarget server -> {
                iconName = server.server().property("icon").orElse("");
                Optional<ServerStatus> status = lobbies.status(server.server());
                if (status.isPresent()) {
                    ServerStatus entry = status.get();
                    reachable = entry.joinable() || current;
                    lore.add(entry.online()
                            ? players(playerLocale, entry.players(), entry.maxPlayers())
                            : locale.render("lobby.selector.offline", playerLocale));
                }
            }
        }
        if (target.premium()) {
            lore.add(locale.render("lobby.selector.premium", playerLocale));
        }
        lore.add(Component.empty());
        String hint = current ? "lobby.selector.here"
                : !permitted ? "lobby.selector.locked"
                : !reachable ? "lobby.selector.unavailable"
                : "lobby.selector.click";
        lore.add(locale.render(hint, playerLocale));
        ItemStack icon = ItemBuilder.of(ConfigText.material(iconName, fallback))
                .name(travel.name(target))
                .lore(lore)
                .glow(current)
                .build();
        boolean leaves = !current && permitted && reachable;
        return new MenuButton(icon, context -> {
            if (leaves) {
                kit.closeLater(context.player());
            }
            travel.travel(context.player(), target);
        });
    }

    private Component players(Locale playerLocale, int players, int max) {
        if (max > 0) {
            return locale.render("lobby.selector.players-max", playerLocale,
                    Placeholder.unparsed("players", String.valueOf(players)),
                    Placeholder.unparsed("max", String.valueOf(max)));
        }
        return locale.render("lobby.selector.players", playerLocale,
                Placeholder.unparsed("players", String.valueOf(players)));
    }
}
