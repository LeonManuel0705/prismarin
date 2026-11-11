package net.prismarin.shared.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import net.prismarin.shared.config.CoreConfig;

final class MongoStorage {

    private MongoStorage() {
        throw new AssertionError();
    }

    static ProfileStorage open(CoreConfig config) {
        MongoClient client = MongoClients.create(config.mongoUri());
        MongoDatabase database = client.getDatabase(config.mongoDatabase());
        return new ProfileStorage(new MongoProfileRepository(database), new MongoDocumentStore(database),
                client::close);
    }
}
