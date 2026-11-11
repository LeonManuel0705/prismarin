package net.prismarin.lobby.extension;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.lobby.api.HotbarClick;
import net.prismarin.lobby.api.LobbyHotbarItem;
import net.prismarin.lobby.api.LobbyMenuEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LobbyExtensions {

    public static final String HOTBAR_PREFIX = "api:";

    public record HotbarIcon(String id, int slot, ItemStack item) {
    }

    private final EntryRegistry<LobbyHotbarItem> hotbar = new EntryRegistry<>("lobby hotbar item",
            LobbyHotbarItem::id, item -> item.slot(), item -> HotbarSlots.problem(item.slot()));
    private final EntryRegistry<LobbyMenuEntry> navigator = menuEntries("navigator entry");
    private final EntryRegistry<LobbyMenuEntry> profile = menuEntries("profile entry");
    private final EntryRegistry<LobbyMenuEntry> selector = menuEntries("lobby selector entry");

    public EntryRegistry<LobbyHotbarItem> hotbar() {
        return hotbar;
    }

    public EntryRegistry<LobbyMenuEntry> navigator() {
        return navigator;
    }

    public EntryRegistry<LobbyMenuEntry> profile() {
        return profile;
    }

    public EntryRegistry<LobbyMenuEntry> selector() {
        return selector;
    }

    public List<HotbarIcon> hotbarIcons(Player player) {
        List<HotbarIcon> icons = new ArrayList<>();
        for (LobbyHotbarItem item : hotbar.all()) {
            if (!hotbar.call(item, () -> item.visible(player)).orElse(false)) {
                continue;
            }
            hotbar.call(item, () -> item.icon(player))
                    .filter(icon -> !icon.isEmpty())
                    .ifPresent(icon -> icons.add(new HotbarIcon(item.id(), item.slot(), icon.clone())));
        }
        return icons;
    }

    public Optional<LobbyHotbarItem> hotbarItem(String taggedId) {
        if (taggedId == null || !taggedId.startsWith(HOTBAR_PREFIX)) {
            return Optional.empty();
        }
        return hotbar.get(taggedId.substring(HOTBAR_PREFIX.length()));
    }

    public void click(LobbyHotbarItem item, HotbarClick click) {
        hotbar.call(item, () -> {
            item.click(click);
            return Boolean.TRUE;
        });
    }

    public List<MenuButton> navigatorButtons(Player viewer) {
        return buttons(navigator, viewer);
    }

    public List<MenuButton> profileButtons(Player viewer) {
        return buttons(profile, viewer);
    }

    public List<MenuButton> selectorButtons(Player viewer) {
        return buttons(selector, viewer);
    }

    private static List<MenuButton> buttons(EntryRegistry<LobbyMenuEntry> registry, Player viewer) {
        if (registry.isEmpty()) {
            return List.of();
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (LobbyMenuEntry entry : registry.all()) {
            if (!registry.call(entry, () -> entry.visible(viewer)).orElse(false)) {
                continue;
            }
            registry.call(entry, () -> entry.icon(viewer))
                    .filter(icon -> !icon.isEmpty())
                    .ifPresent(icon -> buttons.add(new MenuButton(icon.clone(), context -> registry.call(entry,
                            () -> {
                                entry.click(context);
                                return Boolean.TRUE;
                            }))));
        }
        return buttons;
    }

    private static EntryRegistry<LobbyMenuEntry> menuEntries(String kind) {
        return new EntryRegistry<>(kind, LobbyMenuEntry::id, LobbyMenuEntry::order, entry -> null);
    }
}
