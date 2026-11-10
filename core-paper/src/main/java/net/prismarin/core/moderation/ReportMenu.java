package net.prismarin.core.moderation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
public final class ReportMenu {

    public record Reason(String id, String name, String icon) {
    }

    private static final int COLUMNS = 9;
    private static final int WIDE_ROW = 7;

    private final PrismarinCorePlugin plugin;
    private final MenuService menus;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ModerationChannel channel;

    @Inject
    public ReportMenu(PrismarinCorePlugin plugin, MenuService menus, LocaleService locale, ProfileService profiles,
                      ModerationChannel channel) {
        this.plugin = plugin;
        this.menus = menus;
        this.locale = locale;
        this.profiles = profiles;
        this.channel = channel;
    }

    public void open(Player reporter, UUID target, String targetName, List<Reason> reasons) {
        if (reasons.isEmpty()) {
            return;
        }
        Locale viewerLocale = PlayerLocales.of(reporter, profiles);
        List<Integer> slots = slots(reasons.size());
        int rows = slots.getLast() / COLUMNS + 2;
        Menu menu = new Menu(locale.render("report.menu.title", viewerLocale,
                Placeholder.unparsed("player", targetName)), rows);
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, new MenuButton(pane, context -> context.player().updateInventory()));
        }
        ItemBuilder head = ItemBuilder.of(Material.PLAYER_HEAD);
        Player online = plugin.getServer().getPlayer(target);
        if (online != null) {
            head.skullOwner(online);
        }
        menu.button(4, new MenuButton(head
                .name(locale.render("report.menu.target", viewerLocale, Placeholder.unparsed("player", targetName)))
                .lore(locale.render("report.menu.target-lore", viewerLocale))
                .build(), context -> context.player().updateInventory()));
        for (int i = 0; i < reasons.size(); i++) {
            Reason reason = reasons.get(i);
            Material material = Material.matchMaterial(reason.icon());
            ItemStack icon = ItemBuilder.of(material != null && material.isItem() ? material : Material.PAPER)
                    .name(locale.render("report.menu.reason", viewerLocale,
                            Placeholder.component("reason", MiniMessage.miniMessage().deserialize(reason.name()))))
                    .lore(locale.render("report.menu.reason-lore", viewerLocale,
                            Placeholder.unparsed("player", targetName)))
                    .build();
            menu.button(slots.get(i), new MenuButton(icon, context -> submit(context.player(), target, reason)));
        }
        menu.button((rows - 1) * COLUMNS + 4, new MenuButton(ItemBuilder.of(Material.BARRIER)
                .name(locale.render("report.menu.cancel", viewerLocale))
                .build(), context -> close(context.player())));
        menus.open(reporter, menu);
    }

    static List<Integer> slots(int count) {
        List<Integer> slots = new ArrayList<>(count);
        if (count <= 4) {
            int start = 4 - (count - 1);
            for (int i = 0; i < count; i++) {
                slots.add(COLUMNS + start + i * 2);
            }
            return slots;
        }
        int remaining = count;
        int row = 1;
        while (remaining > 0) {
            int inRow = Math.min(WIDE_ROW, remaining);
            int start = (COLUMNS - inRow) / 2;
            for (int i = 0; i < inRow; i++) {
                slots.add(row * COLUMNS + start + i);
            }
            remaining -= inRow;
            row++;
        }
        return slots;
    }

    private void submit(Player reporter, UUID target, Reason reason) {
        channel.sendReport(reporter, target, reason.id());
        reporter.playSound(Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 0.6f, 1.4f),
                Sound.Emitter.self());
        close(reporter);
    }

    private void close(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
