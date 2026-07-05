package net.prismarin.shared.config;

public record CoreConfig(String serverId, StorageMode storageMode, String mongoUri, String mongoDatabase,
                         boolean redisEnabled, String redisHost, int redisPort, String redisPassword,
                         String defaultLocale) {

    public enum StorageMode {
        LOCAL,
        MONGO
    }
}
