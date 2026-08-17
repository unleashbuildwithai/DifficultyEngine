package net.yourserver.coreengine.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats currency amounts for the Market GUI hover lore and chat messages,
 * e.g. unit price $236 alongside a stack-of-10 total of "$2,360 Total".
 */
public final class MoneyFormat {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private MoneyFormat() {
    }

    /**
     * Formats a raw amount with comma thousands-separators and up to 2
     * decimal places (trailing zeros trimmed), e.g. {@code 2360.0 -> "2,360"},
     * {@code 236.5 -> "236.5"}.
     */
    public static String format(double amount) {
        synchronized (FORMAT) {
            return FORMAT.format(amount);
        }
    }

    /** Same as {@link #format(double)} but prefixed with a dollar sign. */
    public static String formatWithSymbol(double amount) {
        return "$" + format(amount);
    }

    /** e.g. {@code formatTotal(236.0) -> "$236 Total"}. */
    public static String formatTotal(double totalAmount) {
        return formatWithSymbol(totalAmount) + " Total";
    }
}
