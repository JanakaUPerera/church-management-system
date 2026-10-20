package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.CashFlowClass;

/** Create or edit an account. A null cash-flow class means "same as the group". */
public record AccountRequest(String code, String name, Long parentId, boolean group, CashFlowClass cashFlowClass,
                             String description) {
}
