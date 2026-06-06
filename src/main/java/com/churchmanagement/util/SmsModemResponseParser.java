package com.churchmanagement.util;

import com.churchmanagement.dto.SmsParsedResponse;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsModemResponseParser {
    private static final Pattern CMGS_PATTERN = Pattern.compile("(?im)^\\s*\\+CMGS:\\s*([^\\r\\n]+)");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("(?i)\\+(?:CMS|CME)\\s+ERROR:\\s*(\\d+)");

    public SmsParsedResponse parseSendResponse(String rawResponse) {
        String raw = rawResponse == null ? "" : rawResponse.strip();
        if (isSuccess(raw)) {
            return new SmsParsedResponse(true, SmsSendStatus.SENT, SmsDeliveryStatus.UNKNOWN,
                    extractCmgsReference(raw), raw, null, null);
        }
        String errorCode = extractErrorCode(raw);
        return new SmsParsedResponse(false, SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED,
                null, raw, errorCode, toFriendlyErrorMessage(raw));
    }

    public boolean isSuccess(String rawResponse) {
        String upper = rawResponse == null ? "" : rawResponse.toUpperCase(Locale.ROOT);
        return upper.contains("+CMGS:") && upper.contains("OK") && !containsError(upper);
    }

    public String extractCmgsReference(String rawResponse) {
        Matcher matcher = CMGS_PATTERN.matcher(rawResponse == null ? "" : rawResponse);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    public String extractErrorCode(String rawResponse) {
        Matcher matcher = ERROR_CODE_PATTERN.matcher(rawResponse == null ? "" : rawResponse);
        return matcher.find() ? matcher.group(1) : null;
    }

    public String toFriendlyErrorMessage(String rawResponse) {
        String errorCode = extractErrorCode(rawResponse);
        if ("10".equals(errorCode)) {
            return "SIM card may not be inserted.";
        }
        if ("21".equals(errorCode)) {
            return "Short message transfer was rejected.";
        }
        if ("27".equals(errorCode)) {
            return "Destination may be out of service.";
        }
        if ("28".equals(errorCode)) {
            return "Subscriber may not be recognized by the network.";
        }
        if ("38".equals(errorCode)) {
            return "Network may be unavailable.";
        }
        if ("500".equals(errorCode)) {
            return "The modem reported an unknown SMS error.";
        }
        String upper = rawResponse == null ? "" : rawResponse.toUpperCase(Locale.ROOT);
        if (upper.contains("NO CARRIER") || upper.contains("NO DIALTONE")) {
            return "Network signal may be weak.";
        }
        if (upper.contains("ERROR")) {
            return "SMS sending failed.";
        }
        return "Unknown modem response.";
    }

    private boolean containsError(String upperResponse) {
        return upperResponse.contains("ERROR")
                || upperResponse.contains("+CMS ERROR")
                || upperResponse.contains("+CME ERROR")
                || upperResponse.contains("NO CARRIER")
                || upperResponse.contains("NO DIALTONE");
    }
}
