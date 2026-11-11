package net.prismarin.core.chat;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.prismarin.api.event.Subscription;
import net.prismarin.core.api.ChatDecorator;
import net.prismarin.core.api.ChatService;
import net.prismarin.core.display.DisplayParts;
import net.prismarin.core.display.PriorityRegistry;
import org.bukkit.entity.Player;

@Singleton
public final class ChatDecorations implements ChatService {

    private final PriorityRegistry<ChatDecorator> decorators = new PriorityRegistry<>("chat decorator");

    @Override
    public Subscription register(ChatDecorator decorator, int priority) {
        return decorators.register(decorator, priority);
    }

    public boolean any() {
        return !decorators.isEmpty();
    }

    public Component prefix(Player source, Audience viewer, Component rankPrefix) {
        List<Component> parts = new ArrayList<>(decorators.collect(decorator -> decorator.prefix(source, viewer)));
        parts.add(rankPrefix);
        return DisplayParts.prefix(parts);
    }

    public Component suffix(Player source, Audience viewer, Component rankSuffix) {
        List<Component> parts = new ArrayList<>();
        parts.add(rankSuffix);
        parts.addAll(decorators.collect(decorator -> decorator.suffix(source, viewer)));
        return DisplayParts.suffix(parts);
    }

    public Component message(Player source, Audience viewer, Component message) {
        Component current = message;
        for (ChatDecorator decorator : decorators.values()) {
            Component input = current;
            current = decorators.call(decorator, () -> decorator.message(source, viewer, input)).orElse(input);
        }
        return current;
    }

    public Map<String, Component> placeholders(Player source, Audience viewer) {
        return DisplayParts.placeholders(decorators.collect(decorator -> decorator.placeholders(source, viewer)),
                RESERVED_PLACEHOLDERS);
    }
}
