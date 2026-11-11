package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@FunctionalInterface
public interface ProxyLink {

    CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body, UUID avoid);

    default CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body) {
        return call(action, body, null);
    }

    static <T> CompletableFuture<T> orNoRoute(CompletableFuture<T> remote, Supplier<CompletableFuture<T>> fallback) {
        return remote.exceptionallyCompose(error -> noRoute(error) ? fallback.get()
                : CompletableFuture.failedFuture(error));
    }

    static boolean noRoute(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof NoRouteException;
    }
}
