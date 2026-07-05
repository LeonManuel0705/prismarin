package net.prismarin.shared.storage;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;

public final class JsonFileProfileRepository implements ProfileRepository {

    private static final int STRIPES = 64;

    private final Path directory;
    private final Gson gson = new Gson();
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public JsonFileProfileRepository(Path dataDirectory) {
        this.directory = dataDirectory.resolve("profiles");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            return readUnlocked(uuid);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void save(PlayerProfile profile) {
        ReentrantLock lock = lockFor(profile.getUuid());
        lock.lock();
        try {
            StoredProfile stored = toStored(profile);
            Optional<PlayerProfile> existing = readUnlocked(profile.getUuid());
            if (existing.isPresent()) {
                stored.coins = existing.get().getCoins();
                stored.gems = existing.get().getGems();
            }
            writeUnlocked(profile.getUuid(), stored);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            Optional<PlayerProfile> found = readUnlocked(uuid);
            if (found.isEmpty()) {
                return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
            }
            PlayerProfile profile = found.get();
            long current = profile.balance(currency);
            long updated = saturatedAdd(current, delta);
            if (!allowNegative && updated < 0) {
                return EconomyResult.failure(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, current);
            }
            profile.balance(currency, updated);
            writeUnlocked(uuid, toStored(profile));
            return EconomyResult.success(updated);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public EconomyResult setCurrency(UUID uuid, Currency currency, long value) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            Optional<PlayerProfile> found = readUnlocked(uuid);
            if (found.isEmpty()) {
                return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
            }
            PlayerProfile profile = found.get();
            profile.balance(currency, value);
            writeUnlocked(uuid, toStored(profile));
            return EconomyResult.success(value);
        } finally {
            lock.unlock();
        }
    }

    private static long saturatedAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException e) {
            return delta > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private Optional<PlayerProfile> readUnlocked(UUID uuid) {
        Path file = fileFor(uuid);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(toProfile(gson.fromJson(json, StoredProfile.class)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeUnlocked(UUID uuid, StoredProfile stored) {
        Path file = fileFor(uuid);
        Path temp = directory.resolve(uuid + ".json.tmp");
        try {
            Files.writeString(temp, gson.toJson(stored), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileFor(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }

    private ReentrantLock lockFor(UUID uuid) {
        return locks[Math.floorMod(uuid.hashCode(), STRIPES)];
    }

    private static StoredProfile toStored(PlayerProfile profile) {
        StoredProfile stored = new StoredProfile();
        stored.uuid = profile.getUuid().toString();
        stored.name = profile.getName();
        stored.firstJoin = profile.getFirstJoin().toEpochMilli();
        stored.lastSeen = profile.getLastSeen().toEpochMilli();
        stored.locale = profile.getLocale();
        stored.coins = profile.getCoins();
        stored.gems = profile.getGems();
        stored.level = profile.getLevel();
        stored.xp = profile.getXp();
        stored.settings = new HashMap<>(profile.getSettings());
        return stored;
    }

    private static PlayerProfile toProfile(StoredProfile stored) {
        return new PlayerProfile(
                UUID.fromString(stored.uuid),
                stored.name,
                Instant.ofEpochMilli(stored.firstJoin),
                Instant.ofEpochMilli(stored.lastSeen),
                stored.locale,
                stored.coins,
                stored.gems,
                stored.level,
                stored.xp,
                stored.settings == null ? new HashMap<>() : stored.settings
        );
    }

    private static final class StoredProfile {
        String uuid;
        String name;
        long firstJoin;
        long lastSeen;
        String locale;
        long coins;
        long gems;
        int level;
        long xp;
        Map<String, String> settings;
    }
}
