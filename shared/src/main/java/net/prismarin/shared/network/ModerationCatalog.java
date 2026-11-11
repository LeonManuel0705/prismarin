package net.prismarin.shared.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.prismarin.api.moderation.PunishmentTemplate;
import net.prismarin.api.moderation.ReportReason;

public final class ModerationCatalog {

    public static final Duration DEFAULT_CLAIM = Duration.ofMinutes(10);
    public static final long MAX_AGE_MILLIS = 60_000L;

    private final ProxyLink link;
    private final Clock clock;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Map<String, ReportReason> ownReasons = new LinkedHashMap<>();
    private final Map<String, PunishmentTemplate> ownTemplates = new LinkedHashMap<>();
    private volatile List<ReportReason> reasons = List.of();
    private volatile List<PunishmentTemplate> templates = List.of();
    private volatile Duration claimTimeout = DEFAULT_CLAIM;
    private volatile long fetchedAt;
    private volatile boolean loaded;

    public ModerationCatalog(ProxyLink link, Clock clock) {
        this.link = link;
        this.clock = clock;
    }

    public List<ReportReason> reasons() {
        refreshWhenStale();
        synchronized (this) {
            return merged(reasons, ownReasons, ReportReason::id);
        }
    }

    public List<PunishmentTemplate> templates() {
        refreshWhenStale();
        synchronized (this) {
            return merged(templates, ownTemplates, PunishmentTemplate::id);
        }
    }

    public Duration claimTimeout() {
        refreshWhenStale();
        return claimTimeout;
    }

    public void registerReason(ReportReason reason) {
        synchronized (this) {
            ownReasons.put(reason.id(), reason);
        }
        send(NetworkCalls.Action.REASON_REGISTER, "reason", ApiJson.tree(reason), () -> forgetReason(reason.id()));
    }

    public void unregisterReason(String id) {
        synchronized (this) {
            ownReasons.remove(id);
            reasons = reasons.stream().filter(reason -> !reason.id().equals(id)).toList();
        }
        send(NetworkCalls.Action.REASON_UNREGISTER, "id", new JsonPrimitive(id), () -> { });
    }

    public void registerTemplate(PunishmentTemplate template) {
        synchronized (this) {
            ownTemplates.put(template.id(), template);
        }
        send(NetworkCalls.Action.TEMPLATE_REGISTER, "template", ApiJson.tree(template),
                () -> forgetTemplate(template.id()));
    }

    public void unregisterTemplate(String id) {
        synchronized (this) {
            ownTemplates.remove(id);
            templates = templates.stream().filter(template -> !template.id().equals(id)).toList();
        }
        send(NetworkCalls.Action.TEMPLATE_UNREGISTER, "id", new JsonPrimitive(id), () -> { });
    }

    public boolean stale() {
        return !loaded || clock.millis() - fetchedAt >= MAX_AGE_MILLIS;
    }

    public void refreshWhenStale() {
        if (stale()) {
            refresh();
        }
    }

    public CompletableFuture<Void> refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        fetchedAt = clock.millis();
        loaded = true;
        return call(NetworkCalls.Action.MODERATION_CATALOG, new JsonObject())
                .thenAccept(this::apply)
                .handle((done, error) -> {
                    refreshing.set(false);
                    if (error != null) {
                        loaded = false;
                    }
                    return null;
                });
    }

    void apply(JsonObject body) {
        List<ReportReason> fetchedReasons = ApiJson.list(body.get("reasons"), ReportReason.class);
        List<PunishmentTemplate> fetchedTemplates = ApiJson.list(body.get("template-list"), PunishmentTemplate.class);
        long claimMillis = ApiJson.longNumber(body, "claim-millis", DEFAULT_CLAIM.toMillis());
        List<ReportReason> missingReasons;
        List<PunishmentTemplate> missingTemplates;
        synchronized (this) {
            reasons = fetchedReasons;
            templates = fetchedTemplates;
            claimTimeout = claimMillis > 0 ? Duration.ofMillis(claimMillis) : Duration.ZERO;
            missingReasons = missing(ownReasons, fetchedReasons, ReportReason::id);
            missingTemplates = missing(ownTemplates, fetchedTemplates, PunishmentTemplate::id);
        }
        missingReasons.forEach(reason -> send(NetworkCalls.Action.REASON_REGISTER, "reason", ApiJson.tree(reason),
                () -> forgetReason(reason.id())));
        missingTemplates.forEach(template -> send(NetworkCalls.Action.TEMPLATE_REGISTER, "template",
                ApiJson.tree(template), () -> forgetTemplate(template.id())));
    }

    private void send(NetworkCalls.Action action, String key, JsonElement value, Runnable refused) {
        JsonObject body = new JsonObject();
        body.add(key, value);
        call(action, body).whenComplete((answer, error) -> {
            if (error != null && refusedByProxy(error)) {
                refused.run();
            }
        });
    }

    private CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body) {
        try {
            return link.call(action, body);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private synchronized void forgetReason(String id) {
        ownReasons.remove(id);
    }

    private synchronized void forgetTemplate(String id) {
        ownTemplates.remove(id);
    }

    private static boolean refusedByProxy(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof RemoteCallException)) {
            cause = cause.getCause();
        }
        return cause != null;
    }

    private static <T> List<T> merged(List<T> fetched, Map<String, T> own, Function<T, String> id) {
        Set<String> known = fetched.stream().map(id).collect(Collectors.toSet());
        List<T> all = new ArrayList<>(fetched);
        own.values().stream().filter(value -> !known.contains(id.apply(value))).forEach(all::add);
        return List.copyOf(all);
    }

    private static <T> List<T> missing(Map<String, T> own, List<T> fetched, Function<T, String> id) {
        Set<String> known = fetched.stream().map(id).collect(Collectors.toSet());
        return own.values().stream().filter(value -> !known.contains(id.apply(value))).toList();
    }
}
