package com.churchmanagement.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Display and parsing of LKR amounts: "1,234.50". */
public final class MoneyFormat {
    private MoneyFormat() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Blank -> null; "1,250" -> 1250.00. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.strip().replace(",", ""));
            return value.scale() < 2 ? value.setScale(2) : value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter an amount like 1,250.00.");
        }
    }
}
