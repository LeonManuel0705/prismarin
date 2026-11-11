package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class PendingCalls {

    public static final String BUS = "bus";

    public record Ticket(int id, CompletableFuture<JsonObject> future) {
    }

    private record Pending(String route, CompletableFuture<JsonObject> future) {
    }

    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public PendingCalls(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Ticket open(String route) {
        int id = ids.updateAndGet(current -> current >= Integer.MAX_VALUE ? 1 : current + 1);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, new Pending(route, future));
        future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).whenComplete((body, error) -> pending.remove(id));
        return new Ticket(id, future);
    }

    public boolean complete(String route, NetworkCalls.Answer answer) {
        Pending entry = pending.get(answer.id());
        if (entry == null || !entry.route().equals(route) || !pending.remove(answer.id(), entry)) {
            return false;
        }
        if (answer.ok()) {
            entry.future().complete(answer.body());
        } else {
            entry.future().completeExceptionally(new RemoteCallException(answer.error()));
        }
        return true;
    }

    public void fail(int id, Throwable error) {
        Pending entry = pending.remove(id);
        if (entry != null) {
            entry.future().completeExceptionally(error);
        }
    }

    public void forget(String route) {
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().route().equals(route)) {
                entry.getValue().future().completeExceptionally(new IllegalStateException("the carrier left"));
                return true;
            }
            return false;
        });
    }

    public int size() {
        return pending.size();
    }
}
