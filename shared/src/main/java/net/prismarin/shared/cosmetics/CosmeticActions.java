package net.prismarin.shared.cosmetics;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEquipEvent;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticPurchaseEvent;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticResult.Status;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.CosmeticUnequippedEvent;
import net.prismarin.api.cosmetics.CosmeticUnlockedEvent;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;

public final class CosmeticActions {

    private static final Logger LOGGER = Logger.getLogger(CosmeticActions.class.getName());

    @FunctionalInterface
    public interface Access {

        boolean permitted(UUID player, Cosmetic cosmetic);
    }

    public record Outcome(CosmeticResult result, PlayerCosmetics state) {

        public Outcome {
            Objects.requireNonNull(result, "result");
        }

        public Optional<PlayerCosmetics> changedState() {
            return Optional.ofNullable(state);
        }
    }

    private final CosmeticCatalog catalog;
    private final CosmeticLedger ledger;
    private final EconomyService economy;
    private final EventBus events;
    private final Executor io;
    private final Executor sync;
    private final Access access;

    public CosmeticActions(CosmeticCatalog catalog, CosmeticLedger ledger, EconomyService economy, EventBus events,
                           Executor io, Executor sync, Access access) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.events = Objects.requireNonNull(events, "events");
        this.io = Objects.requireNonNull(io, "io");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.access = Objects.requireNonNull(access, "access");
    }

    public CompletableFuture<PlayerCosmetics> state(UUID player) {
        return CompletableFuture.supplyAsync(() -> ledger.state(player), io);
    }

    public CompletableFuture<Outcome> purchase(UUID player, String name, String id) {
        Optional<Cosmetic> found = catalog.find(id);
        if (found.isEmpty()) {
            return done(Status.UNKNOWN, null);
        }
        Cosmetic cosmetic = found.get();
        if (!catalog.enabled(cosmetic.slot())) {
            return done(Status.CATEGORY_OFF, cosmetic);
        }
        if (access.permitted(player, cosmetic)) {
            return done(Status.ALREADY_OWNED, cosmetic);
        }
        Optional<Currency> currency = cosmetic.priceTag()
                .flatMap(price -> economy.enabled() ? economy.currency(price.currency()) : Optional.empty());
        if (!cosmetic.purchasable() || currency.isEmpty()) {
            return done(Status.NOT_FOR_SALE, cosmetic);
        }
        CosmeticPrice price = cosmetic.price();
        return state(player).thenComposeAsync(state -> {
            if (state.owns(cosmetic.id())) {
                return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.ALREADY_OWNED,
                        cosmetic), state));
            }
            CosmeticPurchaseEvent event = new CosmeticPurchaseEvent(player, cosmetic, price);
            events.publish(event);
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.CANCELLED, cosmetic),
                        state));
            }
            return pay(player, name, cosmetic, currency.get(), price.amount());
        }, sync).exceptionally(error -> failed("buy " + cosmetic.id() + " for " + player, error, cosmetic));
    }

    public CompletableFuture<Outcome> unlock(UUID player, String name, String id, CosmeticSource source) {
        Optional<Cosmetic> found = catalog.find(id);
        if (found.isEmpty()) {
            return done(Status.UNKNOWN, null);
        }
        Cosmetic cosmetic = found.get();
        CosmeticSource from = source == null ? CosmeticSource.PLUGIN : source;
        return CompletableFuture.supplyAsync(() -> ledger.grant(player, name, cosmetic.id(), from), io)
                .thenApply(change -> {
                    if (!change.changed()) {
                        return new Outcome(CosmeticResult.of(Status.ALREADY_OWNED, cosmetic), change.state());
                    }
                    events.publish(new CosmeticUnlockedEvent(player, cosmetic, from));
                    return new Outcome(CosmeticResult.of(Status.DONE, cosmetic), change.state());
                })
                .exceptionally(error -> failed("unlock " + cosmetic.id() + " for " + player, error, cosmetic));
    }

    public CompletableFuture<Outcome> revoke(UUID player, String id) {
        if (!Cosmetic.validId(id)) {
            return done(Status.UNKNOWN, null);
        }
        Cosmetic cosmetic = catalog.find(id).orElse(null);
        return CompletableFuture.supplyAsync(() -> ledger.revoke(player, id), io)
                .thenApply(change -> {
                    if (!change.changed()) {
                        return new Outcome(CosmeticResult.of(Status.NOT_OWNED, cosmetic), change.state());
                    }
                    if (change.slot() != null) {
                        events.publish(new CosmeticUnequippedEvent(player, id, change.slot(),
                                CosmeticUnequippedEvent.Reason.REVOKED));
                    }
                    return new Outcome(CosmeticResult.of(Status.DONE, cosmetic), change.state());
                })
                .exceptionally(error -> failed("revoke " + id + " from " + player, error, cosmetic));
    }

    public CompletableFuture<Outcome> equip(UUID player, String id) {
        Optional<Cosmetic> found = catalog.find(id);
        if (found.isEmpty()) {
            return done(Status.UNKNOWN, null);
        }
        Cosmetic cosmetic = found.get();
        if (!catalog.enabled(cosmetic.slot())) {
            return done(Status.CATEGORY_OFF, cosmetic);
        }
        boolean permitted = access.permitted(player, cosmetic);
        return state(player).thenComposeAsync(state -> {
            if (!permitted && !state.owns(cosmetic.id())) {
                return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.NOT_OWNED, cosmetic),
                        state));
            }
            String previous = state.equipped(cosmetic.slot()).orElse(null);
            if (cosmetic.id().equals(previous)) {
                return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.ALREADY_EQUIPPED,
                        cosmetic), state));
            }
            CosmeticEquipEvent event = new CosmeticEquipEvent(player, cosmetic, previous);
            events.publish(event);
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.CANCELLED, cosmetic),
                        state));
            }
            return CompletableFuture.supplyAsync(() -> ledger.equip(player, cosmetic), io).thenApply(change -> {
                change.previousId().filter(old -> !old.equals(cosmetic.id())).ifPresent(old -> events.publish(
                        new CosmeticUnequippedEvent(player, old, cosmetic.slot(),
                                CosmeticUnequippedEvent.Reason.REPLACED)));
                return new Outcome(CosmeticResult.of(Status.DONE, cosmetic), change.state());
            });
        }, sync).exceptionally(error -> failed("equip " + cosmetic.id() + " for " + player, error, cosmetic));
    }

    public CompletableFuture<Outcome> unequip(UUID player, CosmeticSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return CompletableFuture.supplyAsync(() -> ledger.unequip(player, slot), io)
                .thenApply(change -> {
                    if (!change.changed()) {
                        return new Outcome(CosmeticResult.of(Status.NOT_EQUIPPED), change.state());
                    }
                    events.publish(new CosmeticUnequippedEvent(player, change.previous(), slot,
                            CosmeticUnequippedEvent.Reason.UNEQUIPPED));
                    return new Outcome(CosmeticResult.of(Status.DONE, catalog.find(change.previous()).orElse(null)),
                            change.state());
                })
                .exceptionally(error -> failed("unequip " + slot.id() + " of " + player, error, null));
    }

    public CompletableFuture<Outcome> unequipAll(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerCosmetics state = ledger.state(player);
            boolean any = false;
            for (Map.Entry<CosmeticSlot, String> entry : state.equipped().entrySet()) {
                CosmeticLedger.Change change = ledger.unequip(player, entry.getKey());
                state = change.state();
                if (change.changed()) {
                    any = true;
                    events.publish(new CosmeticUnequippedEvent(player, change.previous(), entry.getKey(),
                            CosmeticUnequippedEvent.Reason.UNEQUIPPED));
                }
            }
            return new Outcome(CosmeticResult.of(any ? Status.DONE : Status.NOT_EQUIPPED), state);
        }, io).exceptionally(error -> failed("unequip everything of " + player, error, null));
    }

    public CompletableFuture<PlayerCosmetics> hide(UUID player, boolean hidden) {
        return CompletableFuture.supplyAsync(() -> ledger.hide(player, hidden).state(), io);
    }

    private CompletableFuture<Outcome> pay(UUID player, String name, Cosmetic cosmetic, Currency currency,
                                           long amount) {
        return economy.withdraw(player, currency, amount).thenCompose(paid -> {
            if (!paid.success()) {
                CosmeticResult result = paid.failureReason() == EconomyResult.FailureReason.INSUFFICIENT_FUNDS
                        ? CosmeticResult.missing(cosmetic, Math.max(1L, amount - Math.max(0L, paid.newBalance())))
                        : CosmeticResult.of(Status.FAILED, cosmetic);
                return CompletableFuture.completedFuture(new Outcome(result, null));
            }
            return CompletableFuture
                    .supplyAsync(() -> ledger.grant(player, name, cosmetic.id(), CosmeticSource.PURCHASE), io)
                    .handle((change, error) -> {
                        if (error == null && change.changed()) {
                            events.publish(new CosmeticUnlockedEvent(player, cosmetic, CosmeticSource.PURCHASE));
                            return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(Status.DONE,
                                    cosmetic), change.state()));
                        }
                        if (error != null) {
                            LOGGER.log(Level.WARNING, "Could not store the purchase of " + cosmetic.id() + " for "
                                    + player + ", paying it back", error);
                        }
                        Outcome outcome = error == null
                                ? new Outcome(CosmeticResult.of(Status.ALREADY_OWNED, cosmetic), change.state())
                                : new Outcome(CosmeticResult.of(Status.FAILED, cosmetic), null);
                        return refund(player, currency, amount, cosmetic).thenApply(done -> outcome);
                    })
                    .thenCompose(outcome -> outcome);
        });
    }

    private CompletableFuture<Void> refund(UUID player, Currency currency, long amount, Cosmetic cosmetic) {
        return economy.deposit(player, currency, amount).handle((refund, error) -> {
            if (error != null || refund == null || !refund.success()) {
                LOGGER.log(Level.SEVERE, "Could not pay " + amount + " " + currency.id() + " back to " + player
                        + " after the purchase of " + cosmetic.id() + " failed", error);
            }
            return null;
        });
    }

    private static CompletableFuture<Outcome> done(Status status, Cosmetic cosmetic) {
        return CompletableFuture.completedFuture(new Outcome(CosmeticResult.of(status, cosmetic), null));
    }

    private static Outcome failed(String action, Throwable error, Cosmetic cosmetic) {
        LOGGER.log(Level.WARNING, "Could not " + action, error);
        return new Outcome(CosmeticResult.of(Status.FAILED, cosmetic), null);
    }
}
