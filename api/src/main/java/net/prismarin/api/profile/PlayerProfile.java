package net.prismarin.api.profile;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.economy.Currency;

/**
 * Mutable network-wide profile of a single player.
 * Instances are cached per server by the {@link ProfileService} and persisted
 * through the storage layer. Individual field reads and writes are safe from
 * any thread; compound read-modify-write sequences that span multiple calls
 * must be routed through {@link ProfileService#modify}.
 */
public final class PlayerProfile {

    /**
     * Settings key marking that the player explicitly chose a locale.
     * While the value is {@code "true"} the stored locale must not be
     * overwritten with the locale reported by the client.
     */
    public static final String SETTING_LOCALE_OVERRIDE = "locale-override";

    private final UUID uuid;
    private String name;
    private final Instant firstJoin;
    private Instant lastSeen;
    private String locale;
    private long coins;
    private long gems;
    private int level;
    private long xp;
    private final Map<String, String> settings;

    /**
     * Creates a profile from fully known state, typically when loading
     * from storage.
     *
     * @param uuid the player's unique id
     * @param name the player's last known name
     * @param firstJoin the instant of the first network join
     * @param lastSeen the instant the player was last seen
     * @param locale the player's locale as an ISO language tag
     * @param coins the coin balance
     * @param gems the gem balance
     * @param level the current level
     * @param xp the experience collected towards the next level
     * @param settings arbitrary per-player key-value settings
     */
    public PlayerProfile(UUID uuid, String name, Instant firstJoin, Instant lastSeen, String locale,
                         long coins, long gems, int level, long xp, Map<String, String> settings) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.firstJoin = Objects.requireNonNull(firstJoin, "firstJoin");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.coins = coins;
        this.gems = gems;
        this.level = level;
        this.xp = xp;
        this.settings = new ConcurrentHashMap<>(Objects.requireNonNull(settings, "settings"));
    }

    /**
     * Creates a fresh profile for a player joining the network for the
     * first time.
     *
     * @param uuid the player's unique id
     * @param name the player's current name
     * @param locale the default locale to assign
     * @return a new profile with zero balances at level 0
     */
    public static PlayerProfile create(UUID uuid, String name, String locale) {
        Instant now = Instant.now();
        return new PlayerProfile(uuid, name, now, now, locale, 0L, 0L, 0, 0L, Map.of());
    }

    /**
     * Returns the player's unique id.
     *
     * @return the immutable unique id
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Returns the player's last known name.
     *
     * @return the current name
     */
    public synchronized String getName() {
        return name;
    }

    /**
     * Updates the player's name after a name change.
     *
     * @param name the new name
     */
    public synchronized void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * Returns the instant of the very first network join.
     *
     * @return the immutable first-join instant
     */
    public Instant getFirstJoin() {
        return firstJoin;
    }

    /**
     * Returns the instant the player was last seen online.
     *
     * @return the last-seen instant
     */
    public synchronized Instant getLastSeen() {
        return lastSeen;
    }

    /**
     * Updates the last-seen instant.
     *
     * @param lastSeen the new last-seen instant
     */
    public synchronized void setLastSeen(Instant lastSeen) {
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
    }

    /**
     * Returns the player's locale as an ISO language tag such as {@code de}.
     *
     * @return the locale tag
     */
    public synchronized String getLocale() {
        return locale;
    }

    /**
     * Updates the player's locale.
     *
     * @param locale the new locale tag
     */
    public synchronized void setLocale(String locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    /**
     * Returns the coin balance.
     *
     * @return the coin balance
     */
    public synchronized long getCoins() {
        return coins;
    }

    /**
     * Sets the coin balance.
     *
     * @param coins the new coin balance
     */
    public synchronized void setCoins(long coins) {
        this.coins = coins;
    }

    /**
     * Returns the gem balance.
     *
     * @return the gem balance
     */
    public synchronized long getGems() {
        return gems;
    }

    /**
     * Sets the gem balance.
     *
     * @param gems the new gem balance
     */
    public synchronized void setGems(long gems) {
        this.gems = gems;
    }

    /**
     * Returns the balance for the given currency.
     *
     * @param currency the currency to read
     * @return the current balance in that currency
     */
    public synchronized long balance(Currency currency) {
        return currency == Currency.COINS ? coins : gems;
    }

    /**
     * Sets the balance for the given currency.
     *
     * @param currency the currency to write
     * @param value the new balance
     */
    public synchronized void balance(Currency currency, long value) {
        if (currency == Currency.COINS) {
            this.coins = value;
        } else {
            this.gems = value;
        }
    }

    /**
     * Returns the player's current level.
     *
     * @return the level, starting at 0
     */
    public synchronized int getLevel() {
        return level;
    }

    /**
     * Sets the player's level.
     *
     * @param level the new level
     */
    public synchronized void setLevel(int level) {
        this.level = level;
    }

    /**
     * Returns the experience collected towards the next level.
     *
     * @return the current xp amount
     */
    public synchronized long getXp() {
        return xp;
    }

    /**
     * Sets the experience collected towards the next level.
     *
     * @param xp the new xp amount
     */
    public synchronized void setXp(long xp) {
        this.xp = xp;
    }

    /**
     * Returns the mutable per-player settings map.
     * The returned map is safe for concurrent access and backs this
     * profile directly.
     *
     * @return the live settings map backing this profile
     */
    public Map<String, String> getSettings() {
        return settings;
    }

    /**
     * Returns the total experience required to advance from the current
     * level to the next, following the formula {@code 500 + level * 250}.
     *
     * @return the xp threshold for the next level-up
     */
    public synchronized long xpForNextLevel() {
        return xpRequirement(level);
    }

    /**
     * Adds experience and applies as many level-ups as the new total
     * allows, carrying surplus xp into the next level.
     *
     * @param amount the non-negative amount of xp to add
     * @throws IllegalArgumentException if the amount is negative
     */
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
