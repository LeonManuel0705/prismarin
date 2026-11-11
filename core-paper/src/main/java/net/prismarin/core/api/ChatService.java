package net.prismarin.core.api;

import java.util.Set;
import net.prismarin.api.event.Subscription;

public interface ChatService {

    Set<String> RESERVED_PLACEHOLDERS = Set.of("prefix", "name", "suffix", "message", "brand", "brand_prefix",
            "brand_primary", "brand_accent");

    Subscription register(ChatDecorator decorator, int priority);

    default Subscription register(ChatDecorator decorator) {
        return register(decorator, 0);
    }
}
