package net.prismarin.core.cosmetics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.cosmetics.CosmeticMessages.Action;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.gui.MenuStyle;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class CosmeticMenus {

    private static final int ITEM_ROWS = 4;
    private static final int PER_PAGE = ITEM_ROWS * CosmeticLayout.ITEM_COLUMNS;
    private static final int BAR_CELLS = 10;
    private static final TextColor BAR_COLOR = TextColor.color(0x2DD4BF);
    private static final Sound SUCCESS = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.5f,
            1.6f);
    private static final Sound FAILURE = Sound.sound(Key.key("block.note_block.bass"), Sound.Source.MASTER, 0.7f,
            0.7f);

    private final PrismarinCorePlugin plugin;
    private final MenuService menus;
    private final MenuStyle style;
    private final PaperCosmetics cosmetics;
    private final CosmeticTexts texts;

    @Inject
    public CosmeticMenus(PrismarinCorePlugin plugin, MenuService menus, MenuStyle style, PaperCosmetics cosmetics,
                         CosmeticTexts texts) {
        this.plugin = plugin;
        this.menus = menus;
        this.style = style;
        this.cosmetics = cosmetics;
        this.texts = texts;
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, Consumer<Player> back) {
        withState(player, () -> menus.open(player, main(player, back)));
    }

    public void openCategory(Player player, CosmeticSlot slot, Consumer<Player> back) {
        withState(player, () -> menus.open(player, category(player, slot, 0, back)));
    }

    private void withState(Player player, Runnable open) {
        if (cosmetics.cached(player.getUniqueId()).isPresent()) {
            open.run();
            return;
        }
        cosmetics.load(player.getUniqueId()).whenComplete((state, error) -> plugin.getServer().getScheduler()
                .runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        texts.send(player, "cosmetics.failed");
                        return;
                    }
                    open.run();
                }));
    }

    private Menu main(Player player, Consumer<Player> back) {
        Locale language = texts.language(player);
        List<CosmeticCategory> categories = cosmetics.catalog().categories();
        int count = Math.min(categories.size(), CosmeticLayout.ITEM_COLUMNS * 2);
        boolean twoRows = count > CosmeticLayout.ITEM_COLUMNS;
        Menu menu = new Menu(texts.render(language, "cosmetics.menu.title"), 5);
        style.fill(menu);
        style.accent(menu, List.of(0, 8, 36, 44));
        menu.button(4, MenuButton.display(header(player, language)));
        List<Integer> slots = CosmeticLayout.categorySlots(count, twoRows ? 1 : 2);
        for (int index = 0; index < count; index++) {
            CosmeticCategory category = categories.get(index);
            int slot = slots.get(index);
            if (!twoRows) {
                ItemStack pane = pane(category.pane());
                menu.button(slot - CosmeticLayout.COLUMNS, MenuButton.display(pane));
                menu.button(slot + CosmeticLayout.COLUMNS, MenuButton.display(pane));
            }
            menu.button(slot, MenuButton.of(categoryIcon(player, language, category, true),
                    viewer -> menus.open(viewer, category(viewer, category.slot(), 0, back))));
        }
        if (back != null) {
            menu.button(36, style.back(language, back));
        }
        if (cosmetics.economyOn()) {
            menu.button(38, MenuButton.display(wallet(player, language)));
        }
        PlayerCosmetics state = state(player);
        if (!state.equipped().isEmpty()) {
            menu.button(39, MenuButton.of(ItemBuilder.of(Material.BRUSH)
                    .name(texts.render(language, "cosmetics.menu.unequip-all-name"))
                    .lore(texts.render(language, "cosmetics.menu.unequip-all-lore")).build(), viewer ->
                    cosmetics.unequipAll(viewer.getUniqueId()).whenComplete((result, error) -> after(viewer,
                            Action.UNEQUIP_ALL, result, error, null, () -> open(viewer, back)))));
        }
        menu.button(40, style.close(language));
        if (cosmetics.settings().hideCommand()) {
            boolean hiding = state.hidesOthers();
            String mode = hiding ? "on" : "off";
            menu.button(42, MenuButton.of(ItemBuilder.of(hiding ? Material.ENDER_PEARL : Material.ENDER_EYE)
                    .name(texts.render(language, "cosmetics.menu.hide-name-" + mode))
                    .lore(texts.render(language, "cosmetics.menu.hide-lore-" + mode), Component.empty(),
                            texts.render(language, "cosmetics.menu.hide-click"))
                    .build(), viewer -> toggleHide(viewer, () -> open(viewer, back))));
        }
        return menu;
    }

    private Menu category(Player player, CosmeticSlot slot, int page, Consumer<Player> back) {
        Locale language = texts.language(player);
        CosmeticCategory category = cosmetics.catalog().category(slot);
        List<Cosmetic> listed = listed(player, slot);
        int pages = CosmeticLayout.pages(listed.size(), PER_PAGE);
        int shown = Math.clamp(page, 0, pages - 1);
        Menu menu = new Menu(texts.render(language, "cosmetics.menu.category-title",
                Placeholder.component("category", texts.category(category, language))), 6);
        style.fill(menu);
        ItemStack pane = pane(category.pane());
        for (int slotIndex = 0; slotIndex < CosmeticLayout.COLUMNS; slotIndex++) {
            menu.button(slotIndex, MenuButton.display(pane));
            menu.button(45 + slotIndex, MenuButton.display(pane));
        }
        menu.button(4, MenuButton.display(categoryIcon(player, language, category, false)));
        Consumer<Player> reopen = viewer -> menus.open(viewer, category(viewer, slot, shown, back));
        List<Integer> slots = CosmeticLayout.itemSlots(ITEM_ROWS);
        int start = shown * PER_PAGE;
        for (int index = 0; index < PER_PAGE && start + index < listed.size(); index++) {
            Cosmetic cosmetic = listed.get(start + index);
            menu.button(slots.get(index), new MenuButton(item(player, language, cosmetic, true),
                    context -> clicked(context, cosmetic, reopen)));
        }
        if (listed.isEmpty()) {
            menu.button(22, MenuButton.display(ItemBuilder.of(Material.BARRIER)
                    .name(texts.render(language, "cosmetics.menu.empty")).build()));
        }
        menu.button(45, style.back(language, viewer -> menus.open(viewer, main(viewer, back))));
        if (shown > 0) {
            menu.button(47, MenuButton.of(ItemBuilder.of(Material.ARROW)
                    .name(texts.render(language, "menu.previous")).build(),
                    viewer -> menus.open(viewer, category(viewer, slot, shown - 1, back))));
        }
        if (shown + 1 < pages) {
            menu.button(51, MenuButton.of(ItemBuilder.of(Material.ARROW)
                    .name(texts.render(language, "menu.next")).build(),
                    viewer -> menus.open(viewer, category(viewer, slot, shown + 1, back))));
        }
        if (cosmetics.economyOn()) {
            menu.button(49, MenuButton.display(wallet(player, language)));
        }
        cosmetics.equipped(player.getUniqueId(), slot).ifPresent(worn -> menu.button(48, MenuButton.of(
                ItemBuilder.of(Material.BRUSH)
                        .name(texts.render(language, "cosmetics.menu.unequip-slot-name",
                                Placeholder.component("category", texts.category(category, language))))
                        .lore(texts.render(language, "cosmetics.menu.unequip-slot-lore", texts.cosmetic(
                                texts.name(worn, cosmetics.rarityOf(worn), language, player.getUniqueId()))))
                        .build(),
                viewer -> cosmetics.unequip(viewer.getUniqueId(), slot).whenComplete((result, error) ->
                        after(viewer, Action.UNEQUIP, result, error, worn, () -> reopen.accept(viewer))))));
        menu.button(53, style.close(language));
        return menu;
    }

    private Menu confirm(Player player, Cosmetic cosmetic, Consumer<Player> back) {
        Locale language = texts.language(player);
        CosmeticPrice price = cosmetic.price();
        Component priceText = texts.price(price, language);
        Menu menu = new Menu(texts.render(language, "cosmetics.menu.confirm-title"), 3);
        style.fill(menu);
        Component name = texts.name(cosmetic, cosmetics.rarityOf(cosmetic), language, player.getUniqueId());
        MenuButton buy = MenuButton.of(ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                .name(texts.render(language, "cosmetics.menu.confirm-name",
                        Placeholder.component("price", priceText)))
                .lore(texts.render(language, "cosmetics.menu.confirm-lore", texts.cosmetic(name)),
                        texts.render(language, "cosmetics.menu.confirm-balance", Placeholder.component("balance",
                                texts.currency("cosmetics.price", price.currency(),
                                        cosmetics.balance(player, price.currency()), language))))
                .build(), viewer -> buy(viewer, cosmetic, back));
        MenuButton cancel = MenuButton.of(ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .name(texts.render(language, "cosmetics.menu.cancel-name")).build(), back);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                menu.button(row * CosmeticLayout.COLUMNS + column, buy);
                menu.button(row * CosmeticLayout.COLUMNS + 6 + column, cancel);
            }
        }
        menu.button(13, MenuButton.display(item(player, language, cosmetic, false)));
        return menu;
    }

    private void clicked(ClickContext context, Cosmetic cosmetic, Consumer<Player> reopen) {
        Player player = context.player();
        ClickType click = context.clickType();
        if ((click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) && cosmetics.previewable(cosmetic)) {
            if (cosmetics.preview(player, cosmetic)) {
                menus.close(player);
                texts.send(player, "cosmetics.preview", texts.cosmetic(name(player, cosmetic)),
                        Placeholder.unparsed("seconds", Integer.toString(cosmetics.settings().previewSeconds())));
            }
            return;
        }
        UUID uuid = player.getUniqueId();
        if (cosmetics.equipped(uuid, cosmetic.slot()).filter(worn -> worn.id().equals(cosmetic.id())).isPresent()) {
            cosmetics.unequip(uuid, cosmetic.slot()).whenComplete((result, error) -> after(player, Action.UNEQUIP,
                    result, error, cosmetic, () -> reopen.accept(player)));
            return;
        }
        if (cosmetics.ownsHere(player, cosmetic)) {
            cosmetics.equip(uuid, cosmetic.id()).whenComplete((result, error) -> after(player, Action.EQUIP, result,
                    error, cosmetic, () -> reopen.accept(player)));
            return;
        }
        if (cosmetics.forSale(cosmetic)) {
            menus.open(player, confirm(player, cosmetic, reopen));
            return;
        }
        fail(player, cosmetic.permissionOnly() ? "cosmetics.locked-rank" : "cosmetics.locked-reward", cosmetic);
    }

    private void buy(Player player, Cosmetic cosmetic, Consumer<Player> reopen) {
        UUID uuid = player.getUniqueId();
        cosmetics.purchase(uuid, cosmetic.id()).whenComplete((result, error) -> {
            if (error == null && result != null && result.success() && cosmetics.settings().equipAfterPurchase()) {
                after(player, Action.PURCHASE, result, null, cosmetic, () -> {
                });
                cosmetics.equip(uuid, cosmetic.id()).whenComplete((equipped, failure) -> plugin.getServer()
                        .getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                if (failure == null && equipped != null && equipped.success()
                                        && !cosmetics.active(cosmetic.slot())) {
                                    texts.send(player, "cosmetics.equipped-elsewhere",
                                            texts.cosmetic(name(player, cosmetic)));
                                }
                                reopen.accept(player);
                            }
                        }));
                return;
            }
            after(player, Action.PURCHASE, result, error, cosmetic, () -> reopen.accept(player));
        });
    }

    private void toggleHide(Player player, Runnable reopen) {
        boolean hide = !state(player).hidesOthers();
        cosmetics.hideOthers(player.getUniqueId(), hide).whenComplete((hidden, error) -> plugin.getServer()
                .getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        texts.send(player, "cosmetics.failed");
                        return;
                    }
                    texts.send(player, hidden ? "cosmetics.hide-on" : "cosmetics.hide-off");
                    reopen.run();
                }));
    }

    void after(Player player, Action action, CosmeticResult result, Throwable error, Cosmetic fallback,
               Runnable reopen) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || result == null) {
                fail(player, "cosmetics.failed", fallback);
                return;
            }
            Cosmetic cosmetic = result.cosmetic().orElse(fallback);
            String key = CosmeticMessages.key(action, result.status());
            Component name = cosmetic == null ? Component.empty() : name(player, cosmetic);
            Component price = cosmetic != null && cosmetic.purchasable()
                    ? texts.price(cosmetic.price(), texts.language(player)) : Component.empty();
            Component missing = cosmetic != null && cosmetic.purchasable()
                    ? texts.price(CosmeticPrice.of(cosmetic.price().currency(), Math.max(1L, result.missing())),
                    texts.language(player)) : Component.empty();
            player.sendMessage(texts.render(player, key, texts.cosmetic(name), Placeholder.component("price", price),
                    Placeholder.component("missing", missing), Placeholder.unparsed("id",
                            cosmetic == null ? "" : cosmetic.id())));
            player.playSound(result.success() ? SUCCESS : FAILURE, Sound.Emitter.self());
            if (result.success() && action == Action.EQUIP && cosmetic != null && !cosmetics.active(cosmetic.slot())) {
                texts.send(player, "cosmetics.equipped-elsewhere", texts.cosmetic(name));
            }
            reopen.run();
        });
    }

    private void fail(Player player, String key, Cosmetic cosmetic) {
        player.sendMessage(texts.render(player, key, texts.cosmetic(cosmetic == null ? Component.empty()
                : name(player, cosmetic))));
        player.playSound(FAILURE, Sound.Emitter.self());
    }

    private List<Cosmetic> listed(Player player, CosmeticSlot slot) {
        return cosmetics.catalog().of(slot).stream()
                .filter(cosmetic -> !cosmetic.hidden() || cosmetics.ownsHere(player, cosmetic))
                .toList();
    }

    private ItemStack header(Player player, Locale language) {
        List<Cosmetic> all = new ArrayList<>();
        for (CosmeticCategory category : cosmetics.catalog().categories()) {
            all.addAll(listed(player, category.slot()));
        }
        long owned = all.stream().filter(cosmetic -> cosmetics.ownsHere(player, cosmetic)).count();
        int total = all.size();
        int percent = total == 0 ? 0 : (int) Math.min(100L, owned * 100L / total);
        int filled = total == 0 ? 0 : (int) Math.round(percent / 100.0 * BAR_CELLS);
        Component bar = Component.text("■".repeat(filled), BAR_COLOR)
                .append(Component.text("■".repeat(BAR_CELLS - filled), NamedTextColor.DARK_GRAY));
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(texts.render(language, "cosmetics.menu.header-name"))
                .lore(texts.render(language, "cosmetics.menu.header-owned",
                                Placeholder.unparsed("owned", Long.toString(owned)),
                                Placeholder.unparsed("total", Integer.toString(total))),
                        texts.render(language, "cosmetics.menu.header-progress", Placeholder.component("bar", bar),
                                Placeholder.unparsed("percent", Integer.toString(percent))))
                .glow(true)
                .build();
    }

    private ItemStack categoryIcon(Player player, Locale language, CosmeticCategory category, boolean clickable) {
        List<Cosmetic> listed = listed(player, category.slot());
        long owned = listed.stream().filter(cosmetic -> cosmetics.ownsHere(player, cosmetic)).count();
        Optional<Cosmetic> worn = cosmetics.equipped(player.getUniqueId(), category.slot());
        List<Component> lore = new ArrayList<>();
        lore.add(texts.render(language, "cosmetics.menu.category-owned",
                Placeholder.unparsed("owned", Long.toString(owned)),
                Placeholder.unparsed("total", Integer.toString(listed.size()))));
        lore.add(worn.map(cosmetic -> texts.render(language, "cosmetics.menu.category-equipped",
                        texts.cosmetic(name(player, cosmetic))))
                .orElseGet(() -> texts.render(language, "cosmetics.menu.category-none")));
        if (!cosmetics.active(category.slot())) {
            lore.add(texts.render(language, category.slot().effect() ? "cosmetics.menu.category-games"
                    : "cosmetics.menu.category-elsewhere"));
        }
        if (clickable) {
            lore.add(Component.empty());
            lore.add(texts.render(language, "cosmetics.menu.category-click"));
        }
        return ItemBuilder.of(PaperCosmetics.material(category.icon(), Material.CHEST))
                .name(texts.category(category, language))
                .lore(lore)
                .glow(worn.isPresent())
                .build();
    }

    private ItemStack item(Player player, Locale language, Cosmetic cosmetic, boolean clickable) {
        UUID uuid = player.getUniqueId();
        boolean equipped = cosmetics.equipped(uuid, cosmetic.slot()).filter(worn -> worn.id().equals(cosmetic.id()))
                .isPresent();
        boolean stored = state(player).owns(cosmetic.id());
        boolean owned = cosmetics.ownsHere(player, cosmetic);
        List<Component> lore = new ArrayList<>();
        lore.add(texts.render(language, "cosmetics.menu.rarity", Placeholder.component("rarity",
                texts.rarity(cosmetics.rarityOf(cosmetic), language))));
        cosmetic.about().ifPresent(about -> lore.add(texts.render(language, "cosmetics.menu.description",
                Placeholder.component("description", texts.text(about, language, uuid)))));
        lore.add(Component.empty());
        if (equipped) {
            lore.add(texts.render(language, "cosmetics.menu.state-equipped"));
        } else if (stored) {
            lore.add(texts.render(language, "cosmetics.menu.state-owned"));
        } else if (owned) {
            lore.add(texts.render(language, "cosmetics.menu.state-rank"));
        } else if (cosmetics.forSale(cosmetic)) {
            CosmeticPrice price = cosmetic.price();
            lore.add(texts.render(language, "cosmetics.menu.state-price", Placeholder.component("price",
                    texts.price(price, language))));
            long missing = price.amount() - cosmetics.balance(player, price.currency());
            if (missing > 0L) {
                lore.add(texts.render(language, "cosmetics.menu.state-missing", Placeholder.component("missing",
                        texts.price(CosmeticPrice.of(price.currency(), missing), language))));
            }
        } else if (cosmetic.permissionOnly()) {
            lore.add(texts.render(language, "cosmetics.menu.state-locked-rank"));
        } else {
            lore.add(texts.render(language, "cosmetics.menu.state-reward"));
        }
        if (clickable) {
            Set<String> actions = new LinkedHashSet<>();
            if (equipped) {
                actions.add("cosmetics.menu.click-unequip");
            } else if (owned) {
                actions.add("cosmetics.menu.click-equip");
            } else if (cosmetics.forSale(cosmetic)) {
                actions.add("cosmetics.menu.click-buy");
            }
            if (cosmetics.previewable(cosmetic)) {
                actions.add("cosmetics.menu.click-preview");
            }
            if (!actions.isEmpty()) {
                lore.add(Component.empty());
                actions.forEach(key -> lore.add(texts.render(language, key)));
            }
        }
        return cosmetics.iconBuilder(cosmetic)
                .name(texts.name(cosmetic, cosmetics.rarityOf(cosmetic), language, uuid))
                .lore(lore)
                .glow(equipped)
                .build();
    }

    private ItemStack wallet(Player player, Locale language) {
        Set<String> currencies = new LinkedHashSet<>();
        cosmetics.catalog().all().forEach(cosmetic -> cosmetic.priceTag()
                .ifPresent(price -> currencies.add(price.currency())));
        List<Component> lore = new ArrayList<>();
        for (String currency : currencies) {
            lore.add(texts.currency("cosmetics.wallet", currency, cosmetics.balance(player, currency), language));
        }
        return ItemBuilder.of(Material.SUNFLOWER)
                .name(texts.render(language, "cosmetics.menu.wallet-name"))
                .lore(lore)
                .build();
    }

    private Component name(Player player, Cosmetic cosmetic) {
        return texts.name(cosmetic, cosmetics.rarityOf(cosmetic), texts.language(player), player.getUniqueId());
    }

    private PlayerCosmetics state(Player player) {
        return cosmetics.cached(player.getUniqueId()).orElseGet(() -> PlayerCosmetics.none(player.getUniqueId()));
    }

    private static ItemStack pane(String material) {
        return ItemBuilder.of(PaperCosmetics.material(material, Material.LIGHT_BLUE_STAINED_GLASS_PANE))
                .name(Component.space()).build();
    }
}
