package com.churchmanagement.entity;

import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;

/** A chart-of-accounts entry. Group accounts organise the chart; only posting accounts take entries. */
public record Account(Long id, String code, String name, AccountType type, Long parentId, boolean group,
                      CashFlowClass cashFlowClass, String systemKey, String description, Status status) {
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isTopGroup() {
        return parentId == null;
    }

    /** Accounts the system looks up by key (Cash in Hand, Accumulated Fund, income heads). */
    public boolean isSystem() {
        return systemKey != null;
    }

    public String displayText() {
        return code + " " + name;
    }

    public Account withId(long newId) {
        return new Account(newId, code, name, type, parentId, group, cashFlowClass, systemKey, description, status);
    }

    public Account withStatus(Status newStatus) {
        return new Account(id, code, name, type, parentId, group, cashFlowClass, systemKey, description, newStatus);
    }
}
