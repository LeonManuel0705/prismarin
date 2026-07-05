package net.prismarin.core.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder name(Component name) {
        return meta(meta -> meta.displayName(ItemText.sanitize(name)));
    }

    public ItemBuilder name(MiniMessage miniMessage, String raw) {
        return meta(meta -> meta.displayName(ItemText.name(miniMessage, raw)));
    }

    public ItemBuilder name(LocaleService locale, String key, Locale target, TagResolver... resolvers) {
        return name(locale.render(key, target, resolvers));
    }

    public ItemBuilder lore(List<Component> lines) {
        return meta(meta -> meta.lore(lines.stream().map(ItemText::sanitize).toList()));
    }

    public ItemBuilder lore(Component... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder lore(MiniMessage miniMessage, List<String> raws) {
        return meta(meta -> meta.lore(ItemText.lore(miniMessage, raws)));
    }

    public ItemBuilder lore(LocaleService locale, Locale target, String... keys) {
        return lore(Arrays.stream(keys).map(key -> locale.render(key, target)).toList());
    }

    public ItemBuilder glow(boolean glow) {
        return meta(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
    }

    public ItemBuilder skullOwner(OfflinePlayer owner) {
        return meta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
        });
    }

    public ItemStack build() {
        return item.clone();
    }

    private ItemBuilder meta(Consumer<ItemMeta> action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            action.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }
}
