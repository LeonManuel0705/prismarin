package net.prismarin.shared.storage;

import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;

public interface ProfileRepository {

    Optional<PlayerProfile> find(UUID uuid);

    void save(PlayerProfile profile);

    EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative);

    EconomyResult setCurrency(UUID uuid, Currency currency, long value);
}
