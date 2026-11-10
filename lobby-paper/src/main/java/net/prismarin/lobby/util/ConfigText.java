package net.prismarin.lobby.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import org.bukkit.Material;

@Singleton
public final class ConfigText {

    private final LocaleService locale;

    @Inject
    public ConfigText(LocaleService locale) {
        this.locale = locale;
    }

    public Component render(String raw, Locale target, UUID viewer, TagResolver... resolvers) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (!locale.raw(raw, target).equals(raw)) {
            return locale.render(raw, target, viewer, resolvers);
        }
        return locale.format(viewer, raw, resolvers);
    }

    public static Optional<Material> material(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(name.strip());
        if (material == null || !material.isItem() || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(material);
    }

    public static Material material(String name, Material fallback) {
        return material(name).orElse(fallback);
    }
}
