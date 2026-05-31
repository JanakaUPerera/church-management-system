package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;

public interface SmsService {
    SmsResult sendSms(String mobileNumber, String message);
}
