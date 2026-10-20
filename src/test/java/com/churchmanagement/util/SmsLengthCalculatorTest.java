package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsLengthCalculatorTest {
    @Test
    void emptyTextHasNoParts() {
        assertEquals(0, SmsLengthCalculator.septetLength(""));
        assertEquals(0, SmsLengthCalculator.partCount(""));
    }

    @Test
    void partBoundaries() {
        assertEquals(1, SmsLengthCalculator.partCount("a".repeat(160)));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(161)));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(306)));
        assertEquals(3, SmsLengthCalculator.partCount("a".repeat(307)));
        assertEquals(3, SmsLengthCalculator.partCount("a".repeat(459)));
        assertEquals(4, SmsLengthCalculator.partCount("a".repeat(460)));
    }

    @Test
    void extensionCharactersCountTwo() {
        assertEquals(4, SmsLengthCalculator.septetLength("{}"));
        assertEquals(3, SmsLengthCalculator.septetLength("a€"));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(159) + "€"));
    }

    @Test
    void extensionCharacterIsNotSplitAcrossParts() {
        // 152 + 2 (euro) + 10 = 164 septets: the 153-septet cut would fall between ESC and the euro code.
        int[] septets = SmsLengthCalculator.toSeptets("a".repeat(152) + "€" + "b".repeat(10));

        List<int[]> parts = SmsLengthCalculator.splitIntoParts(septets);

        assertEquals(2, parts.size());
        assertEquals(152, parts.get(0).length);
        assertEquals(12, parts.get(1).length);
        assertEquals(0x1B, parts.get(1)[0]);
        assertEquals(0x65, parts.get(1)[1]);
    }

    @Test
    void basicAlphabetCodes() {
        assertArrayEquals(new int[]{0x00, 0x02, 0x01, 0x41, 0x61, 0x0A}, SmsLengthCalculator.toSeptets("@$£Aa\n"));
    }

    @Test
    void findsFirstUnsupportedCharacter() {
        assertNull(SmsLengthCalculator.firstUnsupportedCharacter("Hello £é {test}"));
        assertEquals("අ", SmsLengthCalculator.firstUnsupportedCharacter("Hello අන"));
        assertEquals("`", SmsLengthCalculator.firstUnsupportedCharacter("a`b"));
        assertEquals("😀", SmsLengthCalculator.firstUnsupportedCharacter("Hi 😀"));
        assertTrue(SmsLengthCalculator.isGsm7("Meeting at 10:00 - Head Office"));
        assertFalse(SmsLengthCalculator.isGsm7("අ"));
    }

    @Test
    void toSeptetsRejectsUnsupportedCharacter() {
        assertThrows(IllegalArgumentException.class, () -> SmsLengthCalculator.toSeptets("අ"));
    }
}
