package net.prismarin.shared.messaging;

import com.redis.testcontainers.RedisContainer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.prismarin.api.messaging.NetworkMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
class RedisMessageBusIT {

    @Container
    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7.4.1"));

    private RedisMessageBus publisher;
    private RedisMessageBus subscriber;

    @BeforeEach
    void setUp() {
        String host = REDIS.getHost();
        int port = REDIS.getMappedPort(6379);
        publisher = new RedisMessageBus("lobby-1", host, port, "");
        subscriber = new RedisMessageBus("game-1", host, port, "");
    }

    @AfterEach
    void tearDown() {
        publisher.close();
        subscriber.close();
    }

    @Test
    void messagePublishedOnOneBusArrivesOnAnother() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<NetworkMessage> holder = new AtomicReference<>();
        subscriber.subscribe("party", message -> {
            holder.set(message);
            received.countDown();
        });

        publisher.publish("party", "{\"action\":\"invite\"}");

        assertTrue(received.await(10, TimeUnit.SECONDS));
        NetworkMessage message = holder.get();
        assertNotNull(message.id());
        assertEquals("party", message.channel());
        assertEquals("lobby-1", message.sourceServer());
        assertEquals("{\"action\":\"invite\"}", message.payloadJson());
        assertTrue(message.timestamp() > 0);
    }

    @Test
    void unrelatedChannelsStaySilent() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        subscriber.subscribe("friends", message -> received.countDown());

        publisher.publish("party", "{}");

        assertTrue(!received.await(2, TimeUnit.SECONDS));
    }
}
