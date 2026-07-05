package net.prismarin.api.economy;

import net.prismarin.api.brand.Brand;

/**
 * The virtual currencies available across the PRISMARIN network.
 */
public enum Currency {

    /**
     * The soft currency earned through gameplay.
     */
    COINS("<color:" + Brand.GOLD + ">Coins</color>", "<color:" + Brand.GOLD + ">◆</color>"),

    /**
     * The premium currency granted through ranks and events.
     */
    GEMS("<color:" + Brand.ACCENT + ">Gems</color>", "<color:" + Brand.ACCENT + ">◆</color>");

    private final String display;
    private final String symbol;

    Currency(String display, String symbol) {
        this.display = display;
        this.symbol = symbol;
    }

    /**
     * Returns the MiniMessage token rendering the currency name in its
     * brand color.
     *
     * @return the display token in MiniMessage format
     */
    public String display() {
        return display;
    }

    /**
     * Returns the MiniMessage token rendering the currency glyph in its
     * brand color.
     *
     * @return the symbol token in MiniMessage format
     */
    public String symbol() {
        return symbol;
    }
}
