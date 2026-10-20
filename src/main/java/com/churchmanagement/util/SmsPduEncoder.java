package com.churchmanagement.util;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds SMS-SUBMIT PDUs (GSM 7-bit) for modem PDU mode. Multi-part messages carry an 8-bit
 * concatenation header so the phone joins the parts into one message.
 */
public final class SmsPduEncoder {
    private static final int FIRST_OCTET = 0x31; // SMS-SUBMIT, relative validity, status report requested
    private static final int USER_DATA_HEADER_INDICATOR = 0x40;
    private static final int INTERNATIONAL_NUMBER = 0x91;
    private static final int VALIDITY_24_HOURS = 0xA7; // same as AT+CSMP=49,167,...
    private static final int HEADER_SEPTETS = 7; // 6-octet header + 1 fill bit

    public record SmsPdu(String hex, int tpduLength) {
    }

    private SmsPduEncoder() {
    }

    public static List<SmsPdu> encode(String normalizedNumber, String message, int concatReference) {
        if (normalizedNumber == null || !normalizedNumber.matches("\\+\\d{1,15}")) {
            throw new IllegalArgumentException("Invalid mobile number.");
        }
        List<int[]> parts = SmsLengthCalculator.splitIntoParts(SmsLengthCalculator.toSeptets(message));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("SMS message is required.");
        }
        if (parts.size() > SmsLengthCalculator.MAX_PARTS) {
            throw new IllegalArgumentException("SMS message is longer than " + SmsLengthCalculator.MAX_PARTS + " parts.");
        }
        String digits = normalizedNumber.substring(1);
        boolean multiPart = parts.size() > 1;
        List<SmsPdu> pdus = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            pdus.add(encodePart(digits, parts.get(index), multiPart, concatReference & 0xFF, parts.size(), index + 1));
        }
        return pdus;
    }

    private static SmsPdu encodePart(String digits, int[] septets, boolean multiPart, int reference, int total,
                                     int sequence) {
        ByteArrayOutputStream tpdu = new ByteArrayOutputStream();
        tpdu.write(multiPart ? FIRST_OCTET | USER_DATA_HEADER_INDICATOR : FIRST_OCTET);
        tpdu.write(0x00); // message reference, assigned by the modem
        writeAddress(tpdu, digits);
        tpdu.write(0x00); // protocol identifier
        tpdu.write(0x00); // data coding scheme: GSM 7-bit
        tpdu.write(VALIDITY_24_HOURS);
        if (multiPart) {
            tpdu.write(HEADER_SEPTETS + septets.length);
            tpdu.writeBytes(new byte[]{0x05, 0x00, 0x03, (byte) reference, (byte) total, (byte) sequence});
            tpdu.writeBytes(packSeptets(septets, 1));
        } else {
            tpdu.write(septets.length);
            tpdu.writeBytes(packSeptets(septets, 0));
        }
        byte[] bytes = tpdu.toByteArray();
        return new SmsPdu("00" + HexFormat.of().withUpperCase().formatHex(bytes), bytes.length);
    }

    static void writeAddress(ByteArrayOutputStream out, String digits) {
        out.write(digits.length());
        out.write(INTERNATIONAL_NUMBER);
        String padded = digits.length() % 2 == 0 ? digits : digits + "F";
        for (int index = 0; index < padded.length(); index += 2) {
            int low = Character.digit(padded.charAt(index), 16);
            int high = Character.digit(padded.charAt(index + 1), 16);
            out.write((high << 4) | low);
        }
    }

    static byte[] packSeptets(int[] septets, int fillBits) {
        int totalBits = fillBits + septets.length * 7;
        byte[] packed = new byte[(totalBits + 7) / 8];
        int bitPosition = fillBits;
        for (int septet : septets) {
            for (int bit = 0; bit < 7; bit++) {
                if (((septet >> bit) & 1) != 0) {
                    packed[bitPosition / 8] |= (byte) (1 << (bitPosition % 8));
                }
                bitPosition++;
            }
        }
        return packed;
    }
}
