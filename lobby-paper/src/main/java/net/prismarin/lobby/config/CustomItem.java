package net.prismarin.lobby.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.action.ItemClick;

public record CustomItem(String id, boolean enabled, int slot, String material, String name, List<String> lore,
                         boolean glow, int customModelData, String permission,
                         Map<ItemClick, List<ItemAction>> actions) {

    public CustomItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        name = name == null ? "" : name;
        lore = List.copyOf(lore);
        permission = permission == null ? "" : permission.strip();
        EnumMap<ItemClick, List<ItemAction>> copy = new EnumMap<>(ItemClick.class);
        actions.forEach((click, list) -> copy.put(click, List.copyOf(list)));
        actions = Map.copyOf(copy);
    }

    public List<ItemAction> actionsFor(ItemClick click) {
        List<ItemAction> exact = actions.get(click);
        if (exact != null) {
            return exact;
        }
        return actions.getOrDefault(click.base(), List.of());
    }
}
