package net.prismarin.api.storage;

import java.util.Optional;

public final class StorageApi {

    private static volatile DataStorage storage;

    private StorageApi() {
        throw new AssertionError();
    }

    public static Optional<DataStorage> service() {
        return Optional.ofNullable(storage);
    }

    public static DataStorage get() {
        DataStorage current = storage;
        if (current == null) {
            throw new IllegalStateException("The PRISMARIN storage API is not available. Declare PrismarinCore as a "
                    + "dependency of your plugin.");
        }
        return current;
    }

    public static void register(DataStorage service) {
        storage = service;
    }

    public static void unregister() {
        storage = null;
    }
}
