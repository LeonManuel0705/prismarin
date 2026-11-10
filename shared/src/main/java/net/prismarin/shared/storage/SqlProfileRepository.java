package net.prismarin.shared.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;

public final class SqlProfileRepository implements ProfileRepository {

    private static final Type SETTINGS_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final String SELECT_PROFILE = "SELECT p.name, p.first_join, p.last_seen, p.locale, p.level, p.xp, "
            + "p.settings, b.currency, b.balance FROM prismarin_profiles p "
            + "LEFT JOIN prismarin_balances b ON b.uuid = p.uuid WHERE p.uuid = ?";
    private static final String INSERT_PROFILE = "INSERT INTO prismarin_profiles "
            + "(name, first_join, last_seen, locale, level, xp, settings, uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_PROFILE = "UPDATE prismarin_profiles SET name = ?, first_join = ?, "
            + "last_seen = ?, locale = ?, level = ?, xp = ?, settings = ? WHERE uuid = ?";

    private final SqlDatabase database;
    private final Gson gson = new Gson();
    private final String lockProfile;
    private final String lockBalance;
    private final String upsertBalance;

    public SqlProfileRepository(SqlDatabase database) {
        this.database = database;
        SqlDialect dialect = database.dialect();
        this.lockProfile = "SELECT uuid FROM prismarin_profiles WHERE uuid = ?" + dialect.lockClause();
        this.lockBalance = "SELECT balance FROM prismarin_balances WHERE uuid = ? AND currency = ?"
                + dialect.lockClause();
        this.upsertBalance = dialect.upsert("prismarin_balances", "balance", "uuid", "currency");
        String profiles = "CREATE TABLE IF NOT EXISTS prismarin_profiles ("
                + "uuid CHAR(36) NOT NULL, "
                + "name VARCHAR(64) NOT NULL, "
                + "first_join BIGINT NOT NULL, "
                + "last_seen BIGINT NOT NULL, "
                + "locale VARCHAR(32) NOT NULL, "
                + "level INT NOT NULL, "
                + "xp BIGINT NOT NULL, "
                + "settings TEXT NOT NULL, "
                + "PRIMARY KEY (uuid))" + dialect.tableOptions();
        String balances = "CREATE TABLE IF NOT EXISTS prismarin_balances ("
                + "uuid CHAR(36) NOT NULL, "
                + "currency VARCHAR(32) NOT NULL, "
                + "balance BIGINT NOT NULL, "
                + "PRIMARY KEY (uuid, currency))" + dialect.tableOptions();
        database.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(profiles);
                statement.execute(balances);
            }
            return null;
        });
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_PROFILE)) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return readProfile(uuid, rows);
                }
            }
        });
    }

    @Override
    public void save(PlayerProfile profile) {
        String uuid = profile.getUuid().toString();
        String settings = gson.toJson(new HashMap<>(profile.getSettings()));
        Map<Currency, Long> balances = profile.balances();
        database.transaction(connection -> {
            if (lockProfile(connection, uuid)) {
                writeProfile(connection, UPDATE_PROFILE, uuid, profile, settings);
                return null;
            }
            writeProfile(connection, INSERT_PROFILE, uuid, profile, settings);
            for (Map.Entry<Currency, Long> balance : balances.entrySet()) {
                writeBalance(connection, uuid, balance.getKey(), balance.getValue());
            }
            return null;
        });
    }

    @Override
    public EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative) {
        String id = uuid.toString();
        return database.transaction(connection -> {
            if (!lockProfile(connection, id)) {
                return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
            }
            long current = lockBalance(connection, id, currency);
            long updated = saturatedAdd(current, delta);
            if (!allowNegative && updated < 0) {
                return EconomyResult.failure(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, current);
            }
            writeBalance(connection, id, currency, updated);
            return EconomyResult.success(updated);
        });
    }

    @Override
    public EconomyResult setCurrency(UUID uuid, Currency currency, long value) {
        String id = uuid.toString();
        return database.transaction(connection -> {
            if (!lockProfile(connection, id)) {
                return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
            }
            writeBalance(connection, id, currency, value);
            return EconomyResult.success(value);
        });
    }

    private static long saturatedAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException e) {
            return delta > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private boolean lockProfile(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(lockProfile)) {
            statement.setString(1, uuid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private long lockBalance(Connection connection, String uuid, Currency currency) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(lockBalance)) {
            statement.setString(1, uuid);
            statement.setString(2, currency.id());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    private void writeBalance(Connection connection, String uuid, Currency currency, long balance)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(upsertBalance)) {
            statement.setString(1, uuid);
            statement.setString(2, currency.id());
            statement.setLong(3, balance);
            statement.setLong(4, balance);
            statement.executeUpdate();
        }
    }

    private static void writeProfile(Connection connection, String sql, String uuid, PlayerProfile profile,
                                     String settings) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.getName());
            statement.setLong(2, profile.getFirstJoin().toEpochMilli());
            statement.setLong(3, profile.getLastSeen().toEpochMilli());
            statement.setString(4, profile.getLocale());
            statement.setInt(5, profile.getLevel());
            statement.setLong(6, profile.getXp());
            statement.setString(7, settings);
            statement.setString(8, uuid);
            statement.executeUpdate();
        }
    }

    private Optional<PlayerProfile> readProfile(UUID uuid, ResultSet rows) throws SQLException {
        if (!rows.next()) {
            return Optional.empty();
        }
        String name = rows.getString(1);
        Instant firstJoin = Instant.ofEpochMilli(rows.getLong(2));
        Instant lastSeen = Instant.ofEpochMilli(rows.getLong(3));
        String locale = rows.getString(4);
        int level = rows.getInt(5);
        long xp = rows.getLong(6);
        Map<String, String> settings = gson.fromJson(rows.getString(7), SETTINGS_TYPE);
        Map<Currency, Long> balances = new HashMap<>();
        do {
            String currency = rows.getString(8);
            if (currency != null) {
                balances.put(Currency.of(currency), rows.getLong(9));
            }
        } while (rows.next());
        return Optional.of(new PlayerProfile(uuid, name, firstJoin, lastSeen, locale, balances, level, xp,
                settings == null ? new HashMap<>() : settings));
    }
}
