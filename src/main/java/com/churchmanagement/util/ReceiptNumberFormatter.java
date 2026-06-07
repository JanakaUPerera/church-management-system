package com.churchmanagement.util;

import com.churchmanagement.exception.ReceiptSequenceLimitExceededException;
import com.churchmanagement.service.SystemConfigurationCache;

public class ReceiptNumberFormatter {
    public static final long MAX_SEQUENCE = 999_999L;
    private static final String DEFAULT_PREFIX = "REC";
    private static final int DEFAULT_PADDING = 6;

    private final SystemConfigurationCache configurationCache;
    private final String fallbackPrefix;
    private final int fallbackPadding;

    public ReceiptNumberFormatter() {
        this(null, DEFAULT_PREFIX, DEFAULT_PADDING);
    }

    public ReceiptNumberFormatter(SystemConfigurationCache configurationCache) {
        this(configurationCache, DEFAULT_PREFIX, DEFAULT_PADDING);
    }

    public ReceiptNumberFormatter(String fallbackPrefix, int fallbackPadding) {
        this(null, fallbackPrefix, fallbackPadding);
    }

    private ReceiptNumberFormatter(SystemConfigurationCache configurationCache, String fallbackPrefix,
                                   int fallbackPadding) {
        this.configurationCache = configurationCache;
        this.fallbackPrefix = fallbackPrefix;
        this.fallbackPadding = fallbackPadding;
    }

    public String format(int year, long sequence) {
        int padding = sequencePadding();
        if (sequence < 1 || sequence > maxSequence()) {
            throw new ReceiptSequenceLimitExceededException(year, sequence);
        }

        int twoDigitYear = Math.floorMod(year, 100);
        return receiptPrefix() + String.format("%02d%0" + padding + "d", twoDigitYear, sequence);
    }

    public long maxSequence() {
        return (long) Math.pow(10, sequencePadding()) - 1;
    }

    private String receiptPrefix() {
        if (configurationCache == null) {
            return fallbackPrefix;
        }
        String prefix = configurationCache.getString("receipt.number.prefix");
        return prefix == null || prefix.isBlank() ? fallbackPrefix : prefix;
    }

    private int sequencePadding() {
        if (configurationCache == null) {
            return fallbackPadding;
        }
        try {
            int padding = configurationCache.getInt("receipt.sequence.padding");
            return padding < 4 || padding > 10 ? fallbackPadding : padding;
        } catch (RuntimeException exception) {
            return fallbackPadding;
        }
    }
}
