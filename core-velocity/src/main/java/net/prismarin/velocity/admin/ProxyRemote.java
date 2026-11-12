package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.prismarin.api.admin.AdminApi;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.messaging.NetworkMessage;
import net.prismarin.api.moderation.ModerationApi;
import net.prismarin.api.moderation.ReportFiledEvent;
import net.prismarin.api.storage.StorageApi;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.network.NetworkEvents;
import net.prismarin.shared.network.RemoteDispatcher;
import org.slf4j.Logger;

public final class ProxyRemote {

    private final AdminContext context;
    private final RemoteDispatcher dispatcher;
    private final Logger logger;

    private ProxyRemote(AdminContext context, RemoteDispatcher dispatcher, Logger logger) {
        this.context = context;
        this.dispatcher = dispatcher;
        this.logger = logger;
    }

    public static void register(AdminContext context, ProxyMaintenance maintenance, Logger logger) {
        ProxyNotifications notifications = new ProxyNotifications(context);
        MaintenanceService available = maintenance != null && maintenance.available() ? maintenance : null;
        AdminApi.register(context.services().auditService(), available, notifications);
        StorageApi.register(context.services().dataStorage());
        RemoteDispatcher dispatcher = new RemoteDispatcher(context.services().auditService(),
                () -> Optional.ofNullable(available), notifications, ModerationApi::reports, ModerationApi::templates,
                ModerationApi::alts);
        new ProxyRemote(context, dispatcher, logger).start();
    }

    public static void unregister() {
        AdminApi.unregister();
        StorageApi.unregister();
    }

    private void start() {
        context.channel().handleRemote(this::request);
        if (context.services().config().redisEnabled()) {
            context.services().messageBus().subscribe(NetworkCalls.BUS_REQUESTS, this::busRequest);
        }
        context.services().events().subscribe(ReportFiledEvent.class, this::relay);
    }

    private void request(Player carrier, String server, JsonObject message) {
        Optional<NetworkCalls.Request> request = NetworkCalls.parseRequest(message);
        if (request.isEmpty()) {
            int id = NetworkCalls.id(message);
            if (id > 0) {
                answer(carrier, NetworkCalls.failure(id, "invalid request"));
            }
            return;
        }
        dispatcher.dispatch(request.get(), server).thenAccept(answer -> answer(carrier, answer));
    }

    private void answer(Player carrier, JsonObject answer) {
        answer.addProperty("type", NetworkCalls.ANSWER);
        context.channel().sendSocial(carrier, answer);
    }

    private void busRequest(NetworkMessage message) {
        try {
            JsonObject payload = JsonParser.parseString(message.payloadJson()).getAsJsonObject();
            NetworkCalls.parseRequest(payload).ifPresent(request -> dispatcher.dispatch(request,
                    message.sourceServer()).thenAccept(answer -> {
                        answer.addProperty("to", message.sourceServer());
                        context.services().messageBus().publish(NetworkCalls.BUS_ANSWERS, answer.toString());
                    }));
        } catch (RuntimeException e) {
            logger.warn("Ignoring malformed network request from {}: {}", message.sourceServer(), e.getMessage());
        }
    }

    private void relay(ReportFiledEvent event) {
        NetworkEvents.encode(event).ifPresent(message -> {
            message.addProperty("type", NetworkCalls.EVENT);
            message.addProperty("uuid", NetworkCalls.NOBODY.toString());
            byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
            for (RegisteredServer server : context.proxy().getAllServers()) {
                if (!server.getPlayersConnected().isEmpty()) {
                    server.sendPluginMessage(ModerationChannel.ID, data);
                }
            }
        });
    }
}
