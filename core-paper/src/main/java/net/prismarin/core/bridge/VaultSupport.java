package net.prismarin.core.bridge;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinProvider;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.core.command.CurrencyNames;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

final class VaultSupport {

    private static final long TIMEOUT_SECONDS = 3L;

    private final Plugin plugin;

    VaultSupport(Plugin plugin) {
        this.plugin = plugin;
    }

    Optional<EconomyService> economy() {
        if (!PrismarinProvider.isAvailable()) {
            return Optional.empty();
        }
        EconomyService economy = PrismarinProvider.get().economy();
        return economy.enabled() ? Optional.of(economy) : Optional.empty();
    }

    Optional<Currency> currency(String id) {
        return economy().flatMap(economy -> id == null ? Optional.of(economy.primaryCurrency()) : economy.currency(id));
    }

    List<String> currencyIds() {
        return economy().map(economy -> economy.currencies().stream().map(Currency::id).toList()).orElse(List.of());
    }

    String currencyName(Currency currency) {
        return PlainTextComponentSerializer.plainText().serialize(
                CurrencyNames.render(PrismarinProvider.get().locale(), currency, Locale.ROOT));
    }

    boolean hasAccount(UUID uuid) {
        if (economy().isEmpty()) {
            return false;
        }
        if (PrismarinProvider.get().profiles().getCached(uuid).isPresent()) {
            return true;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.isOnline() || player.hasPlayedBefore();
    }

    Optional<String> accountName(UUID uuid) {
        if (!PrismarinProvider.isAvailable()) {
            return Optional.empty();
        }
        return PrismarinProvider.get().profiles().getCached(uuid)
                .map(profile -> profile.getName())
                .or(() -> Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()));
    }

    long balance(UUID uuid, Currency currency) {
        if (economy().isEmpty()) {
            return 0L;
        }
        return PrismarinProvider.get().profiles().getCached(uuid)
                .map(profile -> profile.balance(currency))
                .orElseGet(() -> await(economy().get().balance(uuid, currency)).orElse(0L));
    }

    Optional<EconomyResult> deposit(UUID uuid, Currency currency, long units) {
        return economy().flatMap(economy -> await(economy.deposit(uuid, currency, units)));
    }

    Optional<EconomyResult> withdraw(UUID uuid, Currency currency, long units) {
        return economy().flatMap(economy -> await(economy.withdraw(uuid, currency, units)));
    }

    Optional<EconomyResult> set(UUID uuid, Currency currency, long units) {
        return economy().flatMap(economy -> await(economy.set(uuid, currency, units)));
    }

    boolean createAccount(UUID uuid, String name) {
        if (economy().isEmpty() || name == null) {
            return false;
        }
        Prismarin prismarin = PrismarinProvider.get();
        if (prismarin.profiles().getCached(uuid).isPresent()) {
            return true;
        }
        boolean online = Bukkit.getPlayer(uuid) != null;
        CompletableFuture<Boolean> created = prismarin.profiles().load(uuid, name)
                .thenCompose(profile -> online
                        ? CompletableFuture.completedFuture(null)
                        : prismarin.profiles().unload(uuid))
                .thenApply(unused -> Boolean.TRUE);
        return await(created).orElse(false);
    }

    static String failureMessage(EconomyResult result) {
        return result.success() ? "" : result.failureReason().name().toLowerCase(Locale.ROOT);
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
}
