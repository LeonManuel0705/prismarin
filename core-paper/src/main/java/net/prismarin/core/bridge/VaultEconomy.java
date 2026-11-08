package net.prismarin.core.bridge;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinProvider;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.core.command.CurrencyNames;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class VaultEconomy implements Economy {

    private static final long TIMEOUT_SECONDS = 3L;

    private final Plugin plugin;

    public VaultEconomy(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return economy().isPresent();
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
        return economy().isPresent() && (player.isOnline() || player.hasPlayedBefore()
                || PrismarinProvider.get().profiles().getCached(player.getUniqueId()).isPresent());
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
        Optional<EconomyService> economy = economy();
        if (economy.isEmpty()) {
            return 0D;
        }
        Currency currency = economy.get().primaryCurrency();
        UUID uuid = player.getUniqueId();
        return PrismarinProvider.get().profiles().getCached(uuid)
                .map(profile -> (double) profile.balance(currency))
                .orElseGet(() -> await(economy.get().balance(uuid, currency)).map(Long::doubleValue).orElse(0D));
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
        if (economy().isEmpty() || player.getName() == null) {
            return false;
        }
        Prismarin prismarin = PrismarinProvider.get();
        UUID uuid = player.getUniqueId();
        if (prismarin.profiles().getCached(uuid).isPresent()) {
            return true;
        }
        CompletableFuture<Void> created = prismarin.profiles().load(uuid, player.getName())
                .thenCompose(profile -> player.isOnline()
                        ? CompletableFuture.completedFuture(null)
                        : prismarin.profiles().unload(uuid));
        return await(created.thenApply(unused -> Boolean.TRUE)).orElse(false);
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
        Optional<EconomyService> economy = economy();
        if (economy.isEmpty()) {
            return new EconomyResponse(amount, 0D, EconomyResponse.ResponseType.FAILURE, "economy is disabled");
        }
        long units = VaultAmounts.toUnits(amount);
        if (units <= 0L) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE,
                    "amount must be a whole number of at least 1");
        }
        Currency currency = economy.get().primaryCurrency();
        CompletableFuture<EconomyResult> pending = deposit
                ? economy.get().deposit(player.getUniqueId(), currency, units)
                : economy.get().withdraw(player.getUniqueId(), currency, units);
        return await(pending)
                .map(result -> new EconomyResponse(units, result.newBalance(),
                        result.success() ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                        result.success() ? "" : result.failureReason().name().toLowerCase(Locale.ROOT)))
                .orElseGet(() -> new EconomyResponse(units, 0D, EconomyResponse.ResponseType.FAILURE,
                        "transaction timed out"));
    }

    private Optional<EconomyService> economy() {
        if (!PrismarinProvider.isAvailable()) {
            return Optional.empty();
        }
        EconomyService economy = PrismarinProvider.get().economy();
        return economy.enabled() ? Optional.of(economy) : Optional.empty();
    }

    private String currencyName() {
        return economy().map(economy -> PlainTextComponentSerializer.plainText().serialize(
                CurrencyNames.render(PrismarinProvider.get().locale(), economy.primaryCurrency(), Locale.ROOT)))
                .orElse("");
    }

    private Optional<OfflinePlayer> player(String name) {
        return Optional.ofNullable(name).map(Bukkit::getOfflinePlayerIfCached);
    }

    private <T> Optional<T> await(CompletableFuture<T> future) {
        try {
            return Optional.ofNullable(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Vault economy call failed", e);
            return Optional.empty();
        }
    }

    private static EconomyResponse unknownPlayer(double amount) {
        return new EconomyResponse(amount, 0D, EconomyResponse.ResponseType.FAILURE, "unknown player");
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0D, 0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "PRISMARIN has no banks");
    }
}
