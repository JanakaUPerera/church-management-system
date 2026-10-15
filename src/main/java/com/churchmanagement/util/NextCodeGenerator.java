package com.churchmanagement.util;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NextCodeGenerator {
    private static final Pattern DIGITS = Pattern.compile("\\d{1,18}");

    private NextCodeGenerator() {
    }

    /**
     * The next code after the highest {@code prefix + digits} code in {@code existingCodes},
     * zero-padded to {@code width}. Codes that don't follow that shape (legacy codes such as
     * {@code REG001}) are ignored, and gaps are not filled — the result is always max + 1.
     */
    public static String next(Collection<String> existingCodes, String prefix, int width) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "(" + DIGITS.pattern() + ")",
                Pattern.CASE_INSENSITIVE);
        long max = 0;
        for (String code : existingCodes) {
            if (code == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(code.strip());
            if (matcher.matches()) {
                max = Math.max(max, Long.parseLong(matcher.group(1)));
            }
        }
        return prefix.toUpperCase(Locale.ROOT) + String.format("%0" + width + "d", max + 1);
    }
}
