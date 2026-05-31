package com.churchmanagement.service;

import com.churchmanagement.enums.ReceiptLanguage;

public class ReceiptFontService {
    public static final String NOTO_SANS = "Noto Sans";
    public static final String NOTO_SANS_SINHALA = "Noto Sans Sinhala";
    public static final String NOTO_SANS_TAMIL = "Noto Sans Tamil";
    public static final String PDF_ENCODING = "Identity-H";

    public String fontFor(ReceiptLanguage language) {
        if (language == ReceiptLanguage.SINHALA) {
            return NOTO_SANS_SINHALA;
        }
        if (language == ReceiptLanguage.TAMIL) {
            return NOTO_SANS_TAMIL;
        }
        return NOTO_SANS;
    }
}
