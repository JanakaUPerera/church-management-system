package com.churchmanagement.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GSM 03.38 7-bit default alphabet rules: which characters an SMS can carry, how many septets a
 * text takes (extension-table characters take two), and how it splits into concatenated parts.
 */
public final class SmsLengthCalculator {
    public static final int SINGLE_PART_LIMIT = 160;
    public static final int MULTI_PART_SEGMENT = 153;
    public static final int MAX_PARTS = 3;
    public static final int MAX_SEPTETS = MULTI_PART_SEGMENT * MAX_PARTS;
    static final int ESCAPE = 0x1B;

    // Index = septet code. Position 0x1B is the escape code and never matches a character.
    private static final String BASIC_ALPHABET =
            "@£$¥èéùìòÇ\nØø\rÅå"
                    + "Δ_ΦΓΛΩΠΨΣΘΞ￿ÆæßÉ"
                    + " !\"#¤%&'()*+,-./"
                    + "0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNO"
                    + "PQRSTUVWXYZÄÖÑÜ§"
                    + "¿abcdefghijklmno"
                    + "pqrstuvwxyzäöñüà";
    private static final String EXTENSION_CHARACTERS = "\f^{}\\[~]|€";
    private static final int[] EXTENSION_CODES = {0x0A, 0x14, 0x28, 0x29, 0x2F, 0x3C, 0x3D, 0x3E, 0x40, 0x65};

    private SmsLengthCalculator() {
    }

    public static String firstUnsupportedCharacter(String text) {
        if (text == null) {
            return null;
        }
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (codePoint > 0xFFFF || (basicCode((char) codePoint) < 0 && extensionCode((char) codePoint) < 0)) {
                return new String(Character.toChars(codePoint));
            }
            index += Character.charCount(codePoint);
        }
        return null;
    }

    public static boolean isGsm7(String text) {
        return firstUnsupportedCharacter(text) == null;
    }

    public static int[] toSeptets(String text) {
        String unsupported = firstUnsupportedCharacter(text);
        if (unsupported != null) {
            throw new IllegalArgumentException("Unsupported SMS character: " + unsupported);
        }
        String safeText = text == null ? "" : text;
        int[] septets = new int[safeText.length() * 2];
        int length = 0;
        for (char character : safeText.toCharArray()) {
            int basic = basicCode(character);
            if (basic >= 0) {
                septets[length++] = basic;
            } else {
                septets[length++] = ESCAPE;
                septets[length++] = extensionCode(character);
            }
        }
        return Arrays.copyOf(septets, length);
    }

    public static int septetLength(String text) {
        return toSeptets(text).length;
    }

    public static int partCount(String text) {
        return splitIntoParts(toSeptets(text)).size();
    }

    public static List<int[]> splitIntoParts(int[] septets) {
        List<int[]> parts = new ArrayList<>();
        if (septets.length == 0) {
            return parts;
        }
        if (septets.length <= SINGLE_PART_LIMIT) {
            parts.add(septets);
            return parts;
        }
        int start = 0;
        while (start < septets.length) {
            int end = Math.min(start + MULTI_PART_SEGMENT, septets.length);
            if (end < septets.length && septets[end - 1] == ESCAPE) {
                end--; // keep an extension character's escape and code in the same part
            }
            parts.add(Arrays.copyOfRange(septets, start, end));
            start = end;
        }
        return parts;
    }

    private static int basicCode(char character) {
        return character == '￿' ? -1 : BASIC_ALPHABET.indexOf(character);
    }

    private static int extensionCode(char character) {
        int index = EXTENSION_CHARACTERS.indexOf(character);
        return index < 0 ? -1 : EXTENSION_CODES[index];
    }
}
