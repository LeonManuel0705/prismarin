package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.admin.NotificationService;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffAlertEvent;
import net.prismarin.api.admin.StaffChat;
import net.prismarin.shared.network.RemoteDispatcher;

public final class ProxyNotifications implements NotificationService, RemoteDispatcher.NoticeSink {

    public static final String PROXY = "proxy";
    public static final String CHAT_PERMISSION = "prismarin.staff.chat";

    private final AdminContext context;

    public ProxyNotifications(AdminContext context) {
        this.context = context;
    }

    @Override
    public CompletableFuture<Delivery> notifyStaff(StaffAlert alert) {
        return staff(alert, PROXY);
    }

    @Override
    public CompletableFuture<Delivery> staffChat(StaffChat message) {
        return chat(message, PROXY);
    }

    @Override
    public CompletableFuture<Delivery> broadcast(Broadcast broadcast) {
        return broadcast(broadcast, PROXY);
    }

    @Override
    public CompletableFuture<Delivery> staff(StaffAlert alert, String origin) {
        List<CommandSource> receivers = new ArrayList<>();
        context.proxy().getAllPlayers().stream().filter(player -> player.hasPermission(alert.permission()))
                .forEach(receivers::add);
        receivers.add(context.proxy().getConsoleCommandSource());
        for (CommandSource receiver : receivers) {
            context.send(receiver, "alerts.plugin", Placeholder.unparsed("source", alert.source()),
                    Placeholder.unparsed("server", server(origin)),
                    Placeholder.component("message", render(receiver, alert.text())));
        }
        context.services().events().publish(new StaffAlertEvent(alert, server(origin)));
        return CompletableFuture.completedFuture(Delivery.DELIVERED);
    }

    @Override
    public CompletableFuture<Delivery> chat(StaffChat message, String origin) {
        context.notifyStaff(CHAT_PERMISSION, "staff.chat", Placeholder.unparsed("player", message.author()),
                Placeholder.unparsed("server", server(origin)), Placeholder.unparsed("message", message.message()));
        return CompletableFuture.completedFuture(Delivery.DELIVERED);
    }

    @Override
    public CompletableFuture<Delivery> broadcast(Broadcast broadcast, String origin) {
        for (Player player : context.proxy().getAllPlayers()) {
            if (InfoCommands.matches(context, player, broadcast.scope())) {
                context.send(player, "alert.message", Placeholder.component("message",
                        render(player, broadcast.text())));
            }
        }
        context.services().audit().record(server(origin), "alert", broadcast.scope(),
                broadcast.text().template(context.messages().fallback()));
        return CompletableFuture.completedFuture(Delivery.DELIVERED);
    }

    private Component render(CommandSource receiver, NoticeText text) {
        TagResolver[] values = text.placeholders().entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return context.services().locale().format(text.template(context.messages().localeOf(receiver)), values);
    }

    private static String server(String origin) {
        return origin == null || origin.isBlank() ? PROXY : origin;
    }
}
