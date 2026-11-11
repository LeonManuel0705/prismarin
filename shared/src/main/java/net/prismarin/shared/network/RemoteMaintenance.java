package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.MaintenanceState;

public final class RemoteMaintenance implements MaintenanceService {

    private final ProxyLink link;

    public RemoteMaintenance(ProxyLink link) {
        this.link = link;
    }

    @Override
    public CompletableFuture<MaintenanceState> state() {
        return link.call(NetworkCalls.Action.MAINTENANCE_STATE, new JsonObject())
                .thenApply(answer -> ApiJson.read(answer.get("state"), MaintenanceState.class));
    }

    @Override
    public CompletableFuture<Boolean> setNetwork(boolean active, String actor) {
        return set("network", null, active, actor);
    }

    @Override
    public CompletableFuture<Boolean> setServer(String server, boolean active, String actor) {
        return set("server", MaintenanceState.name(server), active, actor);
    }

    @Override
    public CompletableFuture<Boolean> setGroup(String group, boolean active, String actor) {
        return set("group", MaintenanceState.name(group), active, actor);
    }

    private CompletableFuture<Boolean> set(String kind, String name, boolean active, String actor) {
        JsonObject body = new JsonObject();
        body.addProperty("kind", kind);
        if (name != null) {
            body.addProperty("name", name);
        }
        body.addProperty("active", active);
        body.addProperty("actor", actor == null ? "" : actor);
        return link.call(NetworkCalls.Action.MAINTENANCE_SET, body)
                .thenApply(answer -> ApiJson.flag(answer, "changed"));
    }
}
