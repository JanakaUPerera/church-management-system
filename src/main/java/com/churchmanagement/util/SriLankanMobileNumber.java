package com.churchmanagement.util;

/** Sri Lankan mobile number rules shared by the modem and the official message recipient preview. */
public final class SriLankanMobileNumber {
    private SriLankanMobileNumber() {
    }

    public static String normalize(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return null;
        }
        String number = mobileNumber.strip().replace(" ", "").replace("-", "");
        if (number.startsWith("+94") && number.length() == 12) {
            return number;
        }
        if (number.startsWith("94") && number.length() == 11) {
            return "+" + number;
        }
        if (number.startsWith("0") && number.length() == 10) {
            return "+94" + number.substring(1);
        }
        return number;
    }

    public static boolean isValid(String normalizedNumber) {
        return normalizedNumber != null && normalizedNumber.matches("\\+947\\d{8}");
    }
}
