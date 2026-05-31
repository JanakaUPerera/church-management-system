package com.churchmanagement.service;

import com.churchmanagement.enums.ReceiptLanguage;

import java.util.Map;

public class ReceiptLabelTranslationService {
    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("receipt_title", "Collection Receipt"),
            Map.entry("receipt_no", "Receipt No"),
            Map.entry("receipt_date_time", "Receipt Date & Time"),
            Map.entry("church_code", "Church Code"),
            Map.entry("church_name", "Church Name"),
            Map.entry("region", "Region"),
            Map.entry("week", "Week"),
            Map.entry("bearer_name", "Bearer Name"),
            Map.entry("submitted_by", "Submitted By"),
            Map.entry("issued_by", "Issued By"),
            Map.entry("collection_type", "Collection Type"),
            Map.entry("amount", "Amount"),
            Map.entry("note", "Note"),
            Map.entry("total_amount", "Total Amount"),
            Map.entry("late_submission", "Late Submission"),
            Map.entry("late_reason", "Late Submission Reason"),
            Map.entry("status", "Status"),
            Map.entry("cancel_reason", "Cancellation Reason"),
            Map.entry("original_receipt", "Original Receipt"),
            Map.entry("cancelled", "CANCELLED"),
            Map.entry("generated_at", "Generated"),
            Map.entry("offertory", "Offertory"),
            Map.entry("tithes", "Tithes"),
            Map.entry("other_donations", "Other Donations")
    );

    private static final Map<String, String> SINHALA = Map.ofEntries(
            Map.entry("receipt_title", "එකතු කිරීමේ රිසිට්පත"),
            Map.entry("receipt_no", "රිසිට් අංකය"),
            Map.entry("receipt_date_time", "රිසිට් දිනය සහ වේලාව"),
            Map.entry("church_code", "දේවස්ථානයේ කේතය"),
            Map.entry("church_name", "දේවස්ථානයේ නම"),
            Map.entry("region", "කලාපය"),
            Map.entry("week", "සතිය"),
            Map.entry("bearer_name", "භාරකරුගේ නම"),
            Map.entry("submitted_by", "ඉදිරිපත් කළේ"),
            Map.entry("issued_by", "නිකුත් කළේ"),
            Map.entry("collection_type", "එකතු කිරීමේ වර්ගය"),
            Map.entry("amount", "මුදල"),
            Map.entry("note", "සටහන"),
            Map.entry("total_amount", "මුළු මුදල"),
            Map.entry("late_submission", "ප්‍රමාද ඉදිරිපත් කිරීම"),
            Map.entry("late_reason", "ප්‍රමාද වීමට හේතුව"),
            Map.entry("status", "තත්ත්වය"),
            Map.entry("cancel_reason", "අවලංගු කිරීමේ හේතුව"),
            Map.entry("original_receipt", "මුල් රිසිට්පත"),
            Map.entry("cancelled", "අවලංගුයි"),
            Map.entry("generated_at", "සකස් කළ දිනය"),
            Map.entry("offertory", "පූජා මුදල්"),
            Map.entry("tithes", "දසයෙන් කොටස"),
            Map.entry("other_donations", "වෙනත් පරිත්‍යාග")
    );

    private static final Map<String, String> TAMIL = Map.ofEntries(
            Map.entry("receipt_title", "காணிக்கை ரசீது"),
            Map.entry("receipt_no", "ரசீது எண்"),
            Map.entry("receipt_date_time", "ரசீது தேதி மற்றும் நேரம்"),
            Map.entry("church_code", "தேவாலய குறியீடு"),
            Map.entry("church_name", "தேவாலய பெயர்"),
            Map.entry("region", "பிராந்தியம்"),
            Map.entry("week", "வாரம்"),
            Map.entry("bearer_name", "பொறுப்பாளர் பெயர்"),
            Map.entry("submitted_by", "சமர்ப்பித்தவர்"),
            Map.entry("issued_by", "வழங்கியவர்"),
            Map.entry("collection_type", "காணிக்கை வகை"),
            Map.entry("amount", "தொகை"),
            Map.entry("note", "குறிப்பு"),
            Map.entry("total_amount", "மொத்த தொகை"),
            Map.entry("late_submission", "தாமத சமர்ப்பிப்பு"),
            Map.entry("late_reason", "தாமத காரணம்"),
            Map.entry("status", "நிலை"),
            Map.entry("cancel_reason", "ரத்து காரணம்"),
            Map.entry("original_receipt", "அசல் ரசீது"),
            Map.entry("cancelled", "ரத்து செய்யப்பட்டது"),
            Map.entry("generated_at", "உருவாக்கப்பட்டது"),
            Map.entry("offertory", "காணிக்கை"),
            Map.entry("tithes", "தசமபாகம்"),
            Map.entry("other_donations", "மற்ற நன்கொடைகள்")
    );

    public String label(String key, ReceiptLanguage language) {
        if (key == null || key.isBlank()) {
            return "";
        }

        Map<String, String> labels = labelsFor(language);
        return labels.getOrDefault(key, ENGLISH.getOrDefault(key, key));
    }

    private Map<String, String> labelsFor(ReceiptLanguage language) {
        if (language == ReceiptLanguage.SINHALA) {
            return SINHALA;
        }
        if (language == ReceiptLanguage.TAMIL) {
            return TAMIL;
        }
        return ENGLISH;
    }
}
