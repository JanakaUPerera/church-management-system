package com.churchmanagement.util;

import com.churchmanagement.dto.SmsParsedResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsModemResponseParserTest {
    private final SmsModemResponseParser parser = new SmsModemResponseParser();

    @Test
    void parseCmgsAcceptedResponse() {
        SmsParsedResponse response = parser.parseSendResponse("+CMGS: 45\r\nOK");

        assertTrue(response.isSuccess());
        assertEquals("45", response.getModemMessageReference());
    }

    @Test
    void parsePlainErrorResponse() {
        SmsParsedResponse response = parser.parseSendResponse("ERROR");

        assertFalse(response.isSuccess());
        assertNull(response.getModemMessageReference());
    }

    @Test
    void parseCmsErrorTen() {
        SmsParsedResponse response = parser.parseSendResponse("+CMS ERROR: 10");

        assertFalse(response.isSuccess());
        assertEquals("10", response.getErrorCode());
        assertEquals("SIM card may not be inserted.", response.getErrorMessage());
    }

    @Test
    void parseCmsErrorThirtyEight() {
        SmsParsedResponse response = parser.parseSendResponse("+CMS ERROR: 38");

        assertFalse(response.isSuccess());
        assertEquals("38", response.getErrorCode());
        assertEquals("Network may be unavailable.", response.getErrorMessage());
    }

    @Test
    void parseUnknownResponse() {
        SmsParsedResponse response = parser.parseSendResponse("READY");

        assertFalse(response.isSuccess());
        assertEquals("Unknown modem response.", response.getErrorMessage());
    }
}
