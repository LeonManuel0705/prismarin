package net.prismarin.api.player;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

public interface PlayerAttribute {

    String key();

    Optional<String> value(UUID viewer, NetworkPlayer target);

    default Optional<Component> profileLine(UUID viewer, NetworkPlayer target, Locale locale) {
        return Optional.empty();
    }

    default int order() {
        return 0;
    }

    static PlayerAttribute of(String key, Function<NetworkPlayer, String> value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return new PlayerAttribute() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public Optional<String> value(UUID viewer, NetworkPlayer target) {
                return Optional.ofNullable(value.apply(target)).filter(text -> !text.isEmpty());
            }
        };
    }
}
