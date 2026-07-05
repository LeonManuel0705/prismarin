package net.prismarin.core.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PagedMenu extends Menu {

    private final Supplier<List<ItemStack>> itemSupplier;
    private final BiConsumer<ClickContext, ItemStack> itemAction;
    private final Component previousLabel;
    private final Component nextLabel;
    private int page;

    public PagedMenu(Component title, int rows, Supplier<List<ItemStack>> itemSupplier,
                     BiConsumer<ClickContext, ItemStack> itemAction, Component previousLabel, Component nextLabel) {
        super(title, rows);
        if (rows < 2) {
            throw new IllegalArgumentException("paged menus need at least 2 rows");
        }
        this.itemSupplier = Objects.requireNonNull(itemSupplier, "itemSupplier");
        this.itemAction = Objects.requireNonNull(itemAction, "itemAction");
        this.previousLabel = Objects.requireNonNull(previousLabel, "previousLabel");
        this.nextLabel = Objects.requireNonNull(nextLabel, "nextLabel");
    }

    public int page() {
        return page;
    }

    @Override
    protected void prepare(Player viewer) {
        clearButtons();
        List<ItemStack> items = List.copyOf(itemSupplier.get());
        int capacity = (rows() - 1) * 9;
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / capacity;
        page = Math.clamp(page, 0, maxPage);
        int start = page * capacity;
        for (int slot = 0; slot < capacity && start + slot < items.size(); slot++) {
            ItemStack stack = items.get(start + slot);
            button(slot, new MenuButton(stack, context -> itemAction.accept(context, stack)));
        }
        int navRow = (rows() - 1) * 9;
        if (page > 0) {
            button(navRow, new MenuButton(navIcon(previousLabel), context -> {
                page--;
                context.menus().open(context.player(), this);
            }));
        }
        if (start + capacity < items.size()) {
            button(navRow + 8, new MenuButton(navIcon(nextLabel), context -> {
                page++;
                context.menus().open(context.player(), this);
            }));
        }
    }

    private static ItemStack navIcon(Component label) {
        return new ItemBuilder(Material.ARROW).name(label).build();
    }
}
