package net.prismarin.core.moderation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class ReportsMenu {

    private final PrismarinCorePlugin plugin;
    private final MenuService menus;
    private final ModerationChannel channel;

    @Inject
    public ReportsMenu(PrismarinCorePlugin plugin, MenuService menus, ModerationChannel channel) {
        this.plugin = plugin;
        this.menus = menus;
        this.channel = channel;
    }

    public void open(Player staff, ReportsView view) {
        MiniMessage mini = MiniMessage.miniMessage();
        Menu menu = new Menu(mini.deserialize(view.title()), view.rows());
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, new MenuButton(pane, context -> context.player().updateInventory()));
        }
        for (ReportsView.Item item : view.items()) {
            Material material = Material.matchMaterial(item.icon());
            ItemBuilder builder = ItemBuilder.of(material != null && material.isItem() ? material : Material.PAPER)
                    .amount(item.amount())
                    .name(mini.deserialize(item.name()))
                    .lore(item.lore().stream().map(mini::deserialize).toList())
                    .glow(item.glow());
            item.skull().ifPresent(uuid -> {
                Player online = plugin.getServer().getPlayer(uuid);
                builder.skullOwner(online != null ? online : plugin.getServer().getOfflinePlayer(uuid));
            });
            menu.button(item.slot(), new MenuButton(builder.build(), context -> click(context.player(), view, item)));
        }
        menus.open(staff, menu);
    }

    private void click(Player staff, ReportsView view, ReportsView.Item item) {
        if (item.clickable()) {
            channel.sendReportsClick(staff, view.session(), item.action());
        } else {
            staff.updateInventory();
        }
        if (item.close()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> staff.closeInventory());
        }
    }
}
