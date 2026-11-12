package net.prismarin.example;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.prismarin.api.Prismarin;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;

final class Queueing {

    private final Prismarin prismarin;
    private final List<Subscription> subscriptions = new ArrayList<>();

    Queueing(Prismarin prismarin) {
        this.prismarin = prismarin;
    }

    void subscribe(Logger logger) {
        subscriptions.add(prismarin.events().subscribe(QueueSentEvent.class, event ->
                logger.info(event.player() + " waited " + event.waitedMillis() / 1000L + " s for "
                        + event.target().id() + " and went to " + event.server() + " with "
                        + event.party().size() + " party members")));
        subscriptions.add(prismarin.events().subscribe(QueueLeftEvent.class, event ->
                logger.info(event.player() + " left the queue for " + event.target().id() + ": "
                        + event.reason().id())));
    }

    CompletableFuture<QueueResult> play(UUID player, String group) {
        return prismarin.queue()
                .map(queue -> queue.join(player, QueueTarget.group(group)))
                .orElseGet(() -> CompletableFuture.completedFuture(QueueResult.of(QueueStatus.DISABLED)));
    }

    CompletableFuture<Boolean> lock(boolean locked) {
        return prismarin.queue()
                .map(queue -> queue.joinable(prismarin.serverId(), !locked))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    void unsubscribe() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }
}
