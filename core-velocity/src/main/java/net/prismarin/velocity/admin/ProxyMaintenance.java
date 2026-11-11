package net.prismarin.velocity.admin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.prismarin.api.admin.MaintenanceChangedEvent;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.MaintenanceState;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.velocity.config.ProxyConfigStore;

public final class ProxyMaintenance implements MaintenanceService {

    public static final String API_ACTOR = "API";

    private final ProxyConfigStore config;
    private final ServerMaintenance servers;
    private final boolean networkSwitch;
    private final CoreServices services;
    private final Executor executor;

    public ProxyMaintenance(ProxyConfigStore config, ServerMaintenance servers, boolean networkSwitch,
                            CoreServices services, Executor executor) {
        this.config = config;
        this.servers = servers;
        this.networkSwitch = networkSwitch;
        this.services = services;
        this.executor = executor;
    }

    public boolean available() {
        return networkSwitch || servers != null;
    }

    public boolean serverSwitch() {
        return servers != null;
    }

    public MaintenanceState current() {
        return MaintenanceState.of(config.maintenance(), servers == null ? Set.of() : servers.targets());
    }

    @Override
    public CompletableFuture<MaintenanceState> state() {
        return CompletableFuture.completedFuture(current());
    }

    @Override
    public synchronized CompletableFuture<Boolean> setNetwork(boolean active, String actor) {
        if (!networkSwitch) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "network maintenance is off on this proxy (modules.maintenance)"));
        }
        if (config.maintenance() == active) {
            return CompletableFuture.completedFuture(false);
        }
        String who = actor(actor);
        return config.setMaintenance(active).handle((saved, error) -> {
            changed(who, "network", active);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> setServer(String server, boolean active, String actor) {
        return toggle(MaintenanceState.SERVER_PREFIX, server, active, actor);
    }

    @Override
    public CompletableFuture<Boolean> setGroup(String group, boolean active, String actor) {
        return toggle(MaintenanceState.GROUP_PREFIX, group, active, actor);
    }

    private CompletableFuture<Boolean> toggle(String prefix, String name, boolean active, String actor) {
        if (servers == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "server maintenance is off on this proxy (modules.server-maintenance)"));
        }
        String target = prefix + MaintenanceState.name(name);
        String who = actor(actor);
        return CompletableFuture.supplyAsync(() -> {
            boolean changed = servers.toggle(target, active);
            if (changed) {
                changed(who, target, active);
            }
            return changed;
        }, executor);
    }

    private void changed(String actor, String target, boolean active) {
        services.audit().record(actor, "maintenance", target, active ? "on" : "off");
        services.events().publish(new MaintenanceChangedEvent(current(), actor));
    }

    private static String actor(String actor) {
        if (actor == null || actor.isBlank()) {
            return API_ACTOR;
        }
        String trimmed = actor.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
