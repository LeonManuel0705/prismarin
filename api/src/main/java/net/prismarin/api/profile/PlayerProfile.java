package net.prismarin.api.profile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.economy.Currency;

public final class PlayerProfile {

    public static final String SETTING_LOCALE_OVERRIDE = "locale-override";

    private final UUID uuid;
    private String name;
    private final Instant firstJoin;
    private Instant lastSeen;
    private String locale;
    private final Map<Currency, Long> balances;
    private int level;
    private long xp;
    private final Map<String, String> settings;

    public PlayerProfile(UUID uuid, String name, Instant firstJoin, Instant lastSeen, String locale,
                         Map<Currency, Long> balances, int level, long xp, Map<String, String> settings) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.firstJoin = Objects.requireNonNull(firstJoin, "firstJoin");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.balances = new HashMap<>(Objects.requireNonNull(balances, "balances"));
        this.level = level;
        this.xp = xp;
        this.settings = new ConcurrentHashMap<>(Objects.requireNonNull(settings, "settings"));
    }

    public static PlayerProfile create(UUID uuid, String name, String locale) {
        Instant now = Instant.now();
        return new PlayerProfile(uuid, name, now, now, locale, Map.of(), 0, 0L, Map.of());
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public Instant getFirstJoin() {
        return firstJoin;
    }

    public synchronized Instant getLastSeen() {
        return lastSeen;
    }

    public synchronized void setLastSeen(Instant lastSeen) {
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
    }

    public synchronized String getLocale() {
        return locale;
    }

    public synchronized void setLocale(String locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public synchronized long balance(Currency currency) {
        return balances.getOrDefault(currency, 0L);
    }

    public synchronized void balance(Currency currency, long value) {
        balances.put(Objects.requireNonNull(currency, "currency"), value);
    }

    public synchronized Map<Currency, Long> balances() {
        return Map.copyOf(balances);
    }

    public synchronized int getLevel() {
        return level;
    }

    public synchronized void setLevel(int level) {
        this.level = level;
    }

    public synchronized long getXp() {
        return xp;
    }

    public synchronized void setXp(long xp) {
        this.xp = xp;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public synchronized long xpForNextLevel() {
        return xpRequirement(level);
    }

    public synchronized void addXp(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        xp += amount;
        while (xp >= xpRequirement(level)) {
            xp -= xpRequirement(level);
            level++;
        }
    }

    private static long xpRequirement(int level) {
        return 500L + (long) level * 250L;
    }
}
