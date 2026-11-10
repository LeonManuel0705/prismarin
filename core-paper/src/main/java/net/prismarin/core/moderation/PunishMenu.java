package net.prismarin.core.moderation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class PunishMenu {

    public record Entry(String id, String icon, String name, List<String> lore) {
    }

    private static final int COLUMNS = 9;

    private final PrismarinCorePlugin plugin;
    private final MenuService menus;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ModerationChannel channel;

    @Inject
    public PunishMenu(PrismarinCorePlugin plugin, MenuService menus, LocaleService locale, ProfileService profiles,
                      ModerationChannel channel) {
        this.plugin = plugin;
        this.menus = menus;
        this.locale = locale;
        this.profiles = profiles;
        this.channel = channel;
    }

    public void open(Player staff, UUID target, String title, String head, List<String> headLore,
                     List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        MiniMessage mini = MiniMessage.miniMessage();
        List<Integer> slots = ReportMenu.slots(entries.size());
        int rows = Math.min(6, slots.getLast() / COLUMNS + 2);
        Menu menu = new Menu(mini.deserialize(title), rows);
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, new MenuButton(pane, context -> context.player().updateInventory()));
        }
        ItemBuilder skull = ItemBuilder.of(Material.PLAYER_HEAD);
        Player online = plugin.getServer().getPlayer(target);
        if (online != null) {
            skull.skullOwner(online);
        }
        menu.button(4, new MenuButton(skull.name(mini.deserialize(head))
                .lore(headLore.stream().map(mini::deserialize).toList())
                .build(), context -> context.player().updateInventory()));
        for (int i = 0; i < entries.size() && slots.get(i) < (rows - 1) * COLUMNS; i++) {
            Entry entry = entries.get(i);
            Material material = Material.matchMaterial(entry.icon());
            ItemStack icon = ItemBuilder.of(material != null && material.isItem() ? material : Material.PAPER)
                    .name(mini.deserialize(entry.name()))
                    .lore(entry.lore().stream().map(mini::deserialize).toList())
                    .build();
            menu.button(slots.get(i), new MenuButton(icon, context -> choose(context.player(), target, entry)));
        }
        Locale viewer = PlayerLocales.of(staff, profiles);
        menu.button((rows - 1) * COLUMNS + 4, new MenuButton(ItemBuilder.of(Material.BARRIER)
                .name(locale.render("report.menu.cancel", viewer))
                .build(), context -> close(context.player())));
        menus.open(staff, menu);
    }

    private void choose(Player staff, UUID target, Entry entry) {
        channel.sendPunish(staff, target, entry.id());
        staff.playSound(Sound.sound(Key.key("block.anvil.place"), Sound.Source.MASTER, 0.4f, 1.6f),
                Sound.Emitter.self());
        close(staff);
    }

    private void close(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
