package net.prismarin.core.bridge;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class VaultModernEconomy implements Economy {

    private final VaultSupport support;

    public VaultModernEconomy(Plugin plugin) {
        this.support = new VaultSupport(plugin);
    }

    @Override
    public boolean isEnabled() {
        return support.economy().isPresent();
    }

    @Override
    public String getName() {
        return "PRISMARIN";
    }

    @Override
    public boolean hasSharedAccountSupport() {
        return false;
    }

    @Override
    public boolean hasMultiCurrencySupport() {
        return true;
    }

    @Override
    public int fractionalDigits(String pluginName) {
        return 0;
    }

    @Override
    public String format(BigDecimal amount) {
        return format(amount, null);
    }

    @Override
    public String format(String pluginName, BigDecimal amount) {
        return format(amount, null);
    }

    @Override
    public String format(BigDecimal amount, String currency) {
        long units = Math.max(0L, VaultAmounts.toUnits(amount));
        return support.currency(currency).map(found -> units + " " + support.currencyName(found))
                .orElse(String.valueOf(units));
    }

    @Override
    public String format(String pluginName, BigDecimal amount, String currency) {
        return format(amount, currency);
    }

    @Override
    public boolean hasCurrency(String currency) {
        return support.economy().flatMap(economy -> economy.currency(currency)).isPresent();
    }

    @Override
    public String getDefaultCurrency(String pluginName) {
        return support.currency(null).map(Currency::id).orElse("");
    }

    @Override
    public String defaultCurrencyNamePlural(String pluginName) {
        return support.currency(null).map(support::currencyName).orElse("");
    }

    @Override
    public String defaultCurrencyNameSingular(String pluginName) {
        return defaultCurrencyNamePlural(pluginName);
    }

    @Override
    public Collection<String> currencies() {
        return support.currencyIds();
    }

    @Override
    public boolean createAccount(UUID accountID, String name) {
        return support.createAccount(accountID, name);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, boolean player) {
        return player && support.createAccount(accountID, name);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName) {
        return support.createAccount(accountID, name);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName, boolean player) {
        return createAccount(accountID, name, player);
    }

    @Override
    public Map<UUID, String> getUUIDNameMap() {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.put(player.getUniqueId(), player.getName());
        }
        return names;
    }

    @Override
    public Optional<String> getAccountName(UUID accountID) {
        return support.accountName(accountID);
    }

    @Override
    public boolean hasAccount(UUID accountID) {
        return support.hasAccount(accountID);
    }

    @Override
    public boolean hasAccount(UUID accountID, String worldName) {
        return support.hasAccount(accountID);
    }

    @Override
    public boolean renameAccount(UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean renameAccount(String pluginName, UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean deleteAccount(String pluginName, UUID accountID) {
        return false;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency) {
        return hasCurrency(currency);
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency, String world) {
        return hasCurrency(currency);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID) {
        return balance(accountID, null);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world) {
        return balance(accountID, null);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world, String currency) {
        return balance(accountID, currency);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, BigDecimal amount) {
        return balance(accountID, null).compareTo(amount) >= 0;
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return has(pluginName, accountID, amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        return balance(accountID, currency).compareTo(amount) >= 0;
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, BigDecimal amount) {
        return set(pluginName, accountID, "", null, amount);
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return set(pluginName, accountID, worldName, null, amount);
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, String worldName, String currency,
                               BigDecimal amount) {
        return transact(accountID, currency, amount, Operation.SET);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return canWithdraw(pluginName, accountID, "", null, amount);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return canWithdraw(pluginName, accountID, worldName, null, amount);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, String worldName, String currency,
                                       BigDecimal amount) {
        Optional<Currency> found = support.currency(currency);
        long units = VaultAmounts.toUnits(amount);
        if (found.isEmpty() || units <= 0L) {
            return failure(amount, BigDecimal.ZERO, found.isEmpty() ? "unknown currency" : "invalid amount");
        }
        long balance = support.balance(accountID, found.get());
        return balance >= units
                ? new EconomyResponse(BigDecimal.valueOf(units), BigDecimal.valueOf(balance),
                        EconomyResponse.ResponseType.SUCCESS, "")
                : failure(BigDecimal.valueOf(units), BigDecimal.valueOf(balance), "insufficient_funds");
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return transact(accountID, null, amount, Operation.WITHDRAW);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return transact(accountID, null, amount, Operation.WITHDRAW);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, String currency,
                                    BigDecimal amount) {
        return transact(accountID, currency, amount, Operation.WITHDRAW);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, BigDecimal amount) {
        return canDeposit(pluginName, accountID, "", null, amount);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return canDeposit(pluginName, accountID, worldName, null, amount);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, String worldName, String currency,
                                      BigDecimal amount) {
        Optional<Currency> found = support.currency(currency);
        long units = VaultAmounts.toUnits(amount);
        if (found.isEmpty() || units <= 0L || !support.hasAccount(accountID)) {
            return failure(amount, BigDecimal.ZERO, found.isEmpty() ? "unknown currency" : "invalid account or amount");
        }
        return new EconomyResponse(BigDecimal.valueOf(units),
                BigDecimal.valueOf(support.balance(accountID, found.get())), EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, BigDecimal amount) {
        return transact(accountID, null, amount, Operation.DEPOSIT);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return transact(accountID, null, amount, Operation.DEPOSIT);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, String currency,
                                   BigDecimal amount) {
        return transact(accountID, currency, amount, Operation.DEPOSIT);
    }

    @Override
    public boolean createSharedAccount(String pluginName, UUID accountID, String name, UUID owner) {
        return false;
    }

    @Override
    public List<String> accountsAccessTo(String pluginName, UUID accountID, AccountPermission... permissions) {
        return List.of();
    }

    @Override
    public List<UUID> accountsWithAccessTo(String pluginName, UUID accountID, AccountPermission... permissions) {
        return List.of();
    }

    @Override
    public boolean isAccountOwner(String pluginName, UUID accountID, UUID uuid) {
        return accountID.equals(uuid);
    }

    @Override
    public boolean setOwner(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean isAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return accountID.equals(uuid);
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid,
                                    AccountPermission... initialPermissions) {
        return false;
    }

    @Override
    public boolean removeAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean hasAccountPermission(String pluginName, UUID accountID, UUID uuid, AccountPermission permission) {
        return accountID.equals(uuid);
    }

    @Override
    public boolean updateAccountPermission(String pluginName, UUID accountID, UUID uuid,
                                           AccountPermission permission, boolean value) {
        return false;
    }

    private BigDecimal balance(UUID accountID, String currency) {
        return support.currency(currency)
                .map(found -> BigDecimal.valueOf(support.balance(accountID, found)))
                .orElse(BigDecimal.ZERO);
    }

    private EconomyResponse transact(UUID accountID, String currency, BigDecimal amount, Operation operation) {
        Optional<Currency> found = support.currency(currency);
        if (found.isEmpty()) {
            return failure(amount, BigDecimal.ZERO, support.economy().isEmpty() ? "economy is disabled"
                    : "unknown currency " + currency);
        }
        long units = VaultAmounts.toUnits(amount);
        if (units < 0L || (units == 0L && operation != Operation.SET)) {
            return failure(amount, balance(accountID, currency), "amount must be a whole number of at least 1");
        }
        Optional<EconomyResult> result = switch (operation) {
            case DEPOSIT -> support.deposit(accountID, found.get(), units);
            case WITHDRAW -> support.withdraw(accountID, found.get(), units);
            case SET -> support.set(accountID, found.get(), units);
        };
        return result
                .map(done -> new EconomyResponse(BigDecimal.valueOf(units), BigDecimal.valueOf(done.newBalance()),
                        done.success() ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                        VaultSupport.failureMessage(done)))
                .orElseGet(() -> failure(BigDecimal.valueOf(units), BigDecimal.ZERO, "transaction timed out"));
    }

    private static EconomyResponse failure(BigDecimal amount, BigDecimal balance, String message) {
        return new EconomyResponse(amount == null ? BigDecimal.ZERO : amount, balance,
                EconomyResponse.ResponseType.FAILURE, message);
    }

    private enum Operation {
        DEPOSIT,
        WITHDRAW,
        SET
    }
}
