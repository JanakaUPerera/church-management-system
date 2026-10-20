package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsPduEncoderTest {
    private static final String ADDRESS = "0B914977214365F7"; // +94771234567

    @Test
    void packsSeptetsLikeTheReferenceVector() {
        byte[] packed = SmsPduEncoder.packSeptets(SmsLengthCalculator.toSeptets("hello"), 0);

        assertEquals("E8329BFD06", HexFormat.of().withUpperCase().formatHex(packed));
    }

    @Test
    void packsWithFillBit() {
        byte[] packed = SmsPduEncoder.packSeptets(new int[]{0x68, 0x65}, 1);

        assertEquals("D065", HexFormat.of().withUpperCase().formatHex(packed));
    }

    @Test
    void encodesSinglePartMessage() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "hello", 0);

        assertEquals(1, pdus.size());
        assertEquals("0031" + "00" + ADDRESS + "0000A7" + "05" + "E8329BFD06", pdus.get(0).hex());
        assertEquals(19, pdus.get(0).tpduLength());
    }

    @Test
    void encodesThreePartMessageWithConcatenationHeader() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "a".repeat(459), 42);

        assertEquals(3, pdus.size());
        for (int part = 1; part <= 3; part++) {
            SmsPduEncoder.SmsPdu pdu = pdus.get(part - 1);
            String expectedPrefix = "0071" + "00" + ADDRESS + "0000A7" + "A0" + "050003" + "2A" + "03" + "0" + part;
            assertTrue(pdu.hex().startsWith(expectedPrefix), "part " + part + ": " + pdu.hex());
            assertEquals(154, pdu.tpduLength());
            assertEquals((pdu.tpduLength() + 1) * 2, pdu.hex().length());
        }
    }

    @Test
    void lastPartLengthMatchesItsSeptets() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "a".repeat(161), 0);

        assertEquals(2, pdus.size());
        assertEquals(154, pdus.get(0).tpduLength());
        assertEquals(28, pdus.get(1).tpduLength()); // 14 header octets + 6 UDH + 8 packed (1 fill bit + 8 septets)
    }

    @Test
    void encodesEvenDigitAddressWithoutPadding() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        SmsPduEncoder.writeAddress(out, "1234");

        assertEquals("04912143", HexFormat.of().withUpperCase().formatHex(out.toByteArray()));
    }

    @Test
    void rejectsMoreThanThreeParts() {
        assertThrows(IllegalArgumentException.class,
                () -> SmsPduEncoder.encode("+94771234567", "a".repeat(460), 0));
    }

    @Test
    void rejectsUnsupportedCharacterAndBadNumber() {
        assertThrows(IllegalArgumentException.class, () -> SmsPduEncoder.encode("+94771234567", "අ", 0));
        assertThrows(IllegalArgumentException.class, () -> SmsPduEncoder.encode("0771234567", "hello", 0));
    }
}
