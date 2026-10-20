package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyFormatTest {
    @Test
    void formatsWithGroupingAndTwoDecimals() {
        assertEquals("1,234,567.50", MoneyFormat.format(new BigDecimal("1234567.5")));
        assertEquals("0.00", MoneyFormat.format(BigDecimal.ZERO));
        assertEquals("-12.00", MoneyFormat.format(new BigDecimal("-12")));
        assertEquals("", MoneyFormat.format(null));
    }

    @Test
    void parsesTypedAmounts() {
        assertEquals(new BigDecimal("1250.00"), MoneyFormat.parse(" 1,250 "));
        assertEquals(new BigDecimal("0.50"), MoneyFormat.parse(".5"));
        assertNull(MoneyFormat.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("12a"));
    }
}
