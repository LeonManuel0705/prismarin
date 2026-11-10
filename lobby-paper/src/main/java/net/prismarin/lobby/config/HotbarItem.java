package net.prismarin.lobby.config;

import java.util.Objects;

public record HotbarItem(String id, boolean enabled, int slot, String material, String hiddenMaterial,
                         String target, String permission) {

    public HotbarItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        hiddenMaterial = hiddenMaterial == null ? "" : hiddenMaterial;
        target = target == null ? "" : target.strip();
        permission = permission == null ? "" : permission.strip();
    }

    public HotbarItem(String id, boolean enabled, int slot, String material, String hiddenMaterial, String target) {
        this(id, enabled, slot, material, hiddenMaterial, target, "");
    }
}
