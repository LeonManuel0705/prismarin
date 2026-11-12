package net.prismarin.lobby.joinme;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.gui.PagedMenu;
import net.prismarin.core.joinme.JoinMeView;
import net.prismarin.core.joinme.PaperJoinMe;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.api.LobbyMenuEntry;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.menu.MenuKit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class JoinMeEntries {

    public static final String ID = "prismarin-joinme";
    private static final int ORDER = -30;
    private static final int ROWS = 4;

    private final LobbyPlugin plugin;
    private final LobbyExtensions extensions;
    private final ModuleState modules;
    private final MenuService menus;
    private final MenuKit kit;
    private final JoinMeView view;
    private final List<Subscription> registered = new ArrayList<>();

    @Inject
    public JoinMeEntries(LobbyPlugin plugin, LobbyExtensions extensions, ModuleState modules, MenuService menus,
                         MenuKit kit, JoinMeView view) {
        this.plugin = plugin;
        this.extensions = extensions;
        this.modules = modules;
        this.menus = menus;
        this.kit = kit;
        this.view = view;
    }

    public void register() {
        if (!modules.enabled(CoreModules.JOINME)) {
            return;
        }
        service().filter(joinMe -> joinMe.settings().navigatorEntry())
                .ifPresent(joinMe -> registered.add(extensions.navigator().register(entry(joinMe))));
    }

    public void unregister() {
        registered.forEach(Subscription::unsubscribe);
        registered.clear();
    }

    private static Optional<PaperJoinMe> service() {
        return JoinMeApi.service().filter(PaperJoinMe.class::isInstance).map(PaperJoinMe.class::cast);
    }

    private LobbyMenuEntry entry(PaperJoinMe joinMe) {
        return new LobbyMenuEntry() {
            @Override
            public String id() {
                return ID;
            }

            @Override
            public ItemStack icon(Player viewer) {
                int open = joinMe.known().size();
                List<Component> lore = new ArrayList<>();
                lore.add(open == 0 ? view.render(viewer, "joinme.menu.none")
                        : view.render(viewer, "joinme.menu.count", Placeholder.unparsed("count",
                        Integer.toString(open))));
                return ItemBuilder.of(Material.FIREWORK_ROCKET).name(view.render(viewer, "joinme.menu.name"))
                        .lore(lore).glow(open > 0).build();
            }

            @Override
            public void click(ClickContext context) {
                open(context.player(), joinMe);
            }

            @Override
            public int order() {
                return ORDER;
            }
        };
    }

    private void open(Player player, PaperJoinMe joinMe) {
        joinMe.visibleTo(player.getUniqueId()).whenComplete((found, error) -> plugin.getServer().getScheduler()
                .runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(view.render(player, "joinme.unavailable"));
                        return;
                    }
                    menus.open(player, menu(player, found));
                }));
    }

    private PagedMenu menu(Player viewer, List<JoinMe> joinMes) {
        Map<ItemStack, String> ids = new IdentityHashMap<>();
        List<ItemStack> heads = new ArrayList<>();
        for (JoinMe joinMe : joinMes) {
            ItemStack head = head(viewer, joinMe);
            ids.put(head, joinMe.id());
            heads.add(head);
        }
        if (heads.isEmpty()) {
            heads.add(ItemBuilder.of(Material.BARRIER).name(view.render(viewer, "joinme.menu.empty")).build());
        }
        PagedMenu menu = new PagedMenu(view.render(viewer, "joinme.menu.title"), ROWS, () -> heads,
                (context, stack) -> {
                    String id = ids.get(stack);
                    if (id != null) {
                        kit.closeLater(context.player());
                        context.player().performCommand("joinme join " + id);
                    }
                }, view.render(viewer, "joinme.menu.previous"), view.render(viewer, "joinme.menu.next"));
        return menu;
    }

    private ItemStack head(Player viewer, JoinMe joinMe) {
        boolean revealed = ViewerRules.revealsRealName(viewer.getUniqueId(), joinMe.creator(),
                viewer.hasPermission(PlayerService.REAL_NAME_PERMISSION));
        TagResolver resolver = view.resolver(viewer, joinMe);
        ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(view.render(viewer, "joinme.menu.head", resolver))
                .lore(List.of(view.render(viewer, "joinme.menu.head-game", resolver),
                        view.render(viewer, "joinme.menu.head-players", resolver),
                        view.render(viewer, "joinme.menu.head-state", resolver),
                        view.render(viewer, "joinme.menu.head-time", resolver),
                        Component.empty(),
                        view.render(viewer, "joinme.menu.head-click", resolver)));
        if (revealed || !joinMe.nicked()) {
            builder.skullOwner(plugin.getServer().getOfflinePlayer(joinMe.creator()));
        }
        return builder.build();
    }
}
