package net.prismarin.core.bridge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class VaultEconomy implements Economy {

    private final VaultSupport support;

    public VaultEconomy(Plugin plugin) {
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
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        long units = Math.max(0L, VaultAmounts.toUnits(amount));
        return units + " " + (units == 1L ? currencyNameSingular() : currencyNamePlural());
    }

    @Override
    public String currencyNamePlural() {
        return currencyName();
    }

    @Override
    public String currencyNameSingular() {
        return currencyName();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return player(playerName).map(this::hasAccount).orElse(false);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return support.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return player(playerName).map(this::getBalance).orElse(0D);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return support.currency(null).map(currency -> (double) support.balance(player.getUniqueId(), currency))
                .orElse(0D);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return player(playerName).map(player -> withdrawPlayer(player, amount)).orElseGet(() -> unknownPlayer(amount));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return transact(player, amount, false);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return player(playerName).map(player -> depositPlayer(player, amount)).orElseGet(() -> unknownPlayer(amount));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return transact(player, amount, true);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return player(playerName).map(this::createPlayerAccount).orElse(false);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return support.createAccount(player.getUniqueId(), player.getName());
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse transact(OfflinePlayer player, double amount, boolean deposit) {
        Optional<Currency> currency = support.currency(null);
        if (currency.isEmpty()) {
            return new EconomyResponse(amount, 0D, EconomyResponse.ResponseType.FAILURE, "economy is disabled");
        }
        long units = VaultAmounts.toUnits(amount);
        if (units <= 0L) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE,
                    "amount must be a whole number of at least 1");
        }
        UUID uuid = player.getUniqueId();
        Optional<EconomyResult> result = deposit
                ? support.deposit(uuid, currency.get(), units)
                : support.withdraw(uuid, currency.get(), units);
        return result
                .map(done -> new EconomyResponse(units, done.newBalance(),
                        done.success() ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                        VaultSupport.failureMessage(done)))
                .orElseGet(() -> new EconomyResponse(units, 0D, EconomyResponse.ResponseType.FAILURE,
                        "transaction timed out"));
    }

    private String currencyName() {
        return support.currency(null).map(support::currencyName).orElse("");
    }

    private Optional<OfflinePlayer> player(String name) {
        return Optional.ofNullable(name).map(Bukkit::getOfflinePlayerIfCached);
    }

    private static EconomyResponse unknownPlayer(double amount) {
        return new EconomyResponse(amount, 0D, EconomyResponse.ResponseType.FAILURE, "unknown player");
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0D, 0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "PRISMARIN has no banks");
    }
}
