package net.prismarin.api.economy;

/**
 * The virtual currencies available across the PRISMARIN network.
 * Display names and glyphs for the currencies are resolved through the
 * locale service via the {@code currency.coins} and {@code currency.gems}
 * message keys.
 */
public enum Currency {

    /**
     * The soft currency earned through gameplay.
     */
    COINS,

    /**
     * The premium currency granted through ranks and events.
     */
    GEMS
}
