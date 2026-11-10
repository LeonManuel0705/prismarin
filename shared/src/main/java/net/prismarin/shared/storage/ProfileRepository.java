package net.prismarin.shared.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;

public interface ProfileRepository {

    Optional<PlayerProfile> find(UUID uuid);

    List<UUID> ids();

    void save(PlayerProfile profile);

    EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative);

    EconomyResult setCurrency(UUID uuid, Currency currency, long value);
}
