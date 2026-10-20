package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;

import java.math.BigDecimal;
import java.util.List;

/** An account in the chart tree with its posted balance in its normal direction. */
public record AccountNode(Account account, BigDecimal balance, List<AccountNode> children) {
}
