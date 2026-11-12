package net.prismarin.lobby.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueueJoinedEvent;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.shared.network.ProxyLink;

@Singleton
public final class QueueBoard {

    static final long STALE_MILLIS = 15_000L;

    private final EventBus events;
    private final Map<String, Integer> sizes = new ConcurrentHashMap<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private volatile long loaded;
    private volatile boolean available;

    @Inject
    public QueueBoard(EventBus events) {
        this.events = events;
    }

    public void start() {
        subscriptions.add(events.subscribe(QueueJoinedEvent.class, event -> update(event.target(), event.size())));
        subscriptions.add(events.subscribe(QueueLeftEvent.class, event -> update(event.target(), event.size())));
        subscriptions.add(events.subscribe(QueueSentEvent.class, event -> update(event.target(), event.size())));
    }

    public void stop() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }

    public int waiting(QueueTarget target) {
        refreshWhenStale();
        return sizes.getOrDefault(target.id(), 0);
    }

    public boolean available() {
        refreshWhenStale();
        return available;
    }

    private void update(QueueTarget target, int size) {
        available = true;
        if (size <= 0) {
            sizes.remove(target.id());
        } else {
            sizes.put(target.id(), size);
        }
    }

    private void refreshWhenStale() {
        if (System.currentTimeMillis() - loaded < STALE_MILLIS) {
            return;
        }
        Optional<QueueService> queue = QueueApi.service();
        if (queue.isEmpty() || !loading.compareAndSet(false, true)) {
            return;
        }
        queue.get().queues().whenComplete((infos, error) -> {
            if (error == null) {
                Map<String, Integer> fresh = new HashMap<>();
                for (QueueInfo info : infos) {
                    if (info.size() > 0) {
                        fresh.put(info.target().id(), info.size());
                    }
                }
                sizes.keySet().retainAll(fresh.keySet());
                sizes.putAll(fresh);
                available = true;
                loaded = System.currentTimeMillis();
            } else if (!ProxyLink.noRoute(error)) {
                available = false;
                loaded = System.currentTimeMillis();
            }
            loading.set(false);
        });
    }
}
