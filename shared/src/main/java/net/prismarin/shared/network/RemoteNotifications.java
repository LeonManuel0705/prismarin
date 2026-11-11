package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.NotificationService;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffChat;

public final class RemoteNotifications implements NotificationService {

    private final ProxyLink link;

    public RemoteNotifications(ProxyLink link) {
        this.link = link;
    }

    @Override
    public CompletableFuture<Delivery> notifyStaff(StaffAlert alert) {
        return send(NetworkCalls.Action.STAFF_ALERT, "alert", Objects.requireNonNull(alert, "alert"));
    }

    @Override
    public CompletableFuture<Delivery> staffChat(StaffChat message) {
        return send(NetworkCalls.Action.STAFF_CHAT, "chat", Objects.requireNonNull(message, "message"));
    }

    @Override
    public CompletableFuture<Delivery> broadcast(Broadcast broadcast) {
        return send(NetworkCalls.Action.BROADCAST, "broadcast", Objects.requireNonNull(broadcast, "broadcast"));
    }

    private CompletableFuture<Delivery> send(NetworkCalls.Action action, String key, Object value) {
        JsonObject body = new JsonObject();
        body.add(key, ApiJson.tree(value));
        CompletableFuture<Delivery> sent = link.call(action, body)
                .thenApply(answer -> Delivery.valueOf(ApiJson.string(answer, "delivery")));
        return ProxyLink.orNoRoute(sent, () -> CompletableFuture.completedFuture(Delivery.NO_ROUTE));
    }
}
