package net.prismarin.shared.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.profile.PlayerProfile;

public final class StorageMigration {

    public static final List<String> COLLECTIONS = List.of("names", "punishments", "reports", "audit", "maintenance");

    public record Result(int profiles, int balances, int documents) {
    }

    private StorageMigration() {
        throw new AssertionError();
    }

    public static Result run(ProfileStorage source, ProfileStorage target) {
        int profiles = 0;
        int balances = 0;
        for (UUID uuid : source.repository().ids()) {
            PlayerProfile profile = source.repository().find(uuid).orElse(null);
            if (profile == null) {
                continue;
            }
            target.repository().save(profile);
            for (Map.Entry<Currency, Long> balance : profile.balances().entrySet()) {
                target.repository().setCurrency(uuid, balance.getKey(), balance.getValue());
                balances++;
            }
            profiles++;
        }
        int documents = 0;
        for (String collection : COLLECTIONS) {
            for (Map.Entry<String, String> entry : source.documents().entries(collection).entrySet()) {
                target.documents().save(collection, entry.getKey(), entry.getValue());
                documents++;
            }
        }
        return new Result(profiles, balances, documents);
    }
}
