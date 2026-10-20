package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SriLankanMobileNumberTest {
    @Test
    void normalizesLocalAndInternationalForms() {
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("0771234567"));
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("94771234567"));
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("+94 77-123-4567"));
        assertNull(SriLankanMobileNumber.normalize("  "));
        assertNull(SriLankanMobileNumber.normalize(null));
    }

    @Test
    void validatesMobileNumbersOnly() {
        assertTrue(SriLankanMobileNumber.isValid("+94771234567"));
        assertFalse(SriLankanMobileNumber.isValid("+94111234567"));
        assertFalse(SriLankanMobileNumber.isValid("0771234567"));
        assertFalse(SriLankanMobileNumber.isValid(null));
    }
}
