package net.prismarin.shared.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.nio.file.Path;
import net.prismarin.shared.config.CoreConfig;

public final class ProfileStorage implements AutoCloseable {

    private final ProfileRepository repository;
    private final DocumentStore documents;
    private final Runnable closer;

    private ProfileStorage(ProfileRepository repository, DocumentStore documents, Runnable closer) {
        this.repository = repository;
        this.documents = documents;
        this.closer = closer;
    }

    public static ProfileStorage create(CoreConfig config, Path dataDirectory) {
        return switch (config.storageMode()) {
            case LOCAL -> new ProfileStorage(new JsonFileProfileRepository(dataDirectory),
                    new JsonFileDocumentStore(dataDirectory), () -> { });
            case MONGO -> mongo(config);
            case MYSQL -> sql(SqlDatabase.mysql(config.sql()));
            case SQLITE -> sql(SqlDatabase.sqlite(dataDirectory.resolve(config.sql().sqliteFile())));
        };
    }

    public ProfileRepository repository() {
        return repository;
    }

    public DocumentStore documents() {
        return documents;
    }

    @Override
    public void close() {
        try {
            documents.close();
        } finally {
            closer.run();
        }
    }

    private static ProfileStorage mongo(CoreConfig config) {
        MongoClient client = MongoClients.create(config.mongoUri());
        MongoDatabase database = client.getDatabase(config.mongoDatabase());
        return new ProfileStorage(new MongoProfileRepository(database), new MongoDocumentStore(database),
                client::close);
    }

    private static ProfileStorage sql(SqlDatabase database) {
        try {
            return new ProfileStorage(new SqlProfileRepository(database), new SqlDocumentStore(database),
                    database::close);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }
}
