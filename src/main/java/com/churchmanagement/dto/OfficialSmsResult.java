package com.churchmanagement.dto;

import java.util.List;

public record OfficialSmsResult(String batchUuid, int queuedCount, List<OfficialSmsRecipient> skipped,
                                int partCount) {
}
