package net.prismarin.api.brand;

/**
 * Central branding constants for the PRISMARIN network.
 * All colors are given as MiniMessage-compatible hex strings and every
 * user-facing text component should be styled through these values.
 */
public final class Brand {

    /**
     * The network name as displayed to players.
     */
    public static final String NAME = "PRISMARIN";

    /**
     * Primary brand color, a bright cyan.
     */
    public static final String PRIMARY = "#22D3EE";

    /**
     * Accent brand color, a teal used for gradients and highlights.
     */
    public static final String ACCENT = "#2DD4BF";

    /**
     * Dark background color used on scoreboards and menus.
     */
    public static final String BG_DARK = "#0F172A";

    /**
     * Gold color reserved for currency and premium highlights.
     */
    public static final String GOLD = "#FBBF24";

    /**
     * Muted text color name for secondary information.
     */
    public static final String TEXT_MUTED = "gray";

    /**
     * The chat prefix in MiniMessage format, ending with an open gray tag
     * so that appended message bodies are muted by default.
     */
    public static final String PREFIX =
            "<gradient:" + PRIMARY + ":" + ACCENT + "><b>" + NAME + "</b></gradient> <dark_gray>|</dark_gray> <gray>";

    private Brand() {
        throw new AssertionError();
    }

    /**
     * Wraps the given text in the brand gradient.
     *
     * @param text the raw text to style
     * @return a MiniMessage string rendering the text in the brand gradient
     */
    public static String gradient(String text) {
        return "<gradient:" + PRIMARY + ":" + ACCENT + ">" + text + "</gradient>";
    }
}
