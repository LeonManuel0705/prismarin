package net.prismarin.shared.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Tag("integration")
@Testcontainers
class MongoDocumentStoreIT extends DocumentStoreContract {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private MongoClient client;
    private MongoDocumentStore store;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        store = new MongoDocumentStore(client.getDatabase("prismarin-it"));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Override
    protected DocumentStore store() {
        return store;
    }
}
