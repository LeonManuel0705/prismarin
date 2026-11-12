package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.Component;
import net.prismarin.api.event.Subscription;

public final class PlayerInfoLines {

    @FunctionalInterface
    public interface Line {

        Optional<Component> render(CommandSource viewer, UUID player);
    }

    private static final List<Line> LINES = new CopyOnWriteArrayList<>();

    private PlayerInfoLines() {
        throw new AssertionError();
    }

    public static Subscription add(Line line) {
        LINES.add(line);
        return () -> LINES.remove(line);
    }

    public static List<Component> render(CommandSource viewer, UUID player) {
        List<Component> rendered = new ArrayList<>();
        for (Line line : LINES) {
            try {
                line.render(viewer, player).ifPresent(rendered::add);
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return rendered;
    }
}
