package com.team19.splitsmart.util;

import java.math.BigDecimal;
import java.util.Locale;

/** All money is stored and calculated as whole cents in a long (NFR-3). Convert only at the UI. */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** 334 -> "$3.34", -400 -> "-$4.00". */
    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format(Locale.US, "%s$%d.%02d", sign, abs / 100, abs % 100);
    }

    /**
     * "3.34" -> 334. Throws NumberFormatException if the text isn't a number or has more than
     * two decimal places. Callers still check that the amount is greater than zero.
     */
    public static long parseToCents(String text) {
        BigDecimal value = new BigDecimal(text.trim());
        if (value.scale() > 2) {
            throw new NumberFormatException("More than two decimal places");
        }
        return value.movePointRight(2).longValueExact();
    }
}
