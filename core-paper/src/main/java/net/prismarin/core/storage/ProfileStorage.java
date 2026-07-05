package net.prismarin.core.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Path;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import net.prismarin.shared.storage.MongoProfileRepository;
import net.prismarin.shared.storage.ProfileRepository;

public final class ProfileStorage implements AutoCloseable {

    private final ProfileRepository repository;
    private final MongoClient client;

    private ProfileStorage(ProfileRepository repository, MongoClient client) {
        this.repository = repository;
        this.client = client;
    }

    public static ProfileStorage create(CoreConfig config, Path dataDirectory) {
        if (config.storageMode() == CoreConfig.StorageMode.MONGO) {
            MongoClient client = MongoClients.create(config.mongoUri());
            return new ProfileStorage(new MongoProfileRepository(client.getDatabase(config.mongoDatabase())), client);
        }
        return new ProfileStorage(new JsonFileProfileRepository(dataDirectory), null);
    }

    public ProfileRepository repository() {
        return repository;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
