package com.churchmanagement.service;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.JournalRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The single OPENING entry: asset and liability balances and the Accumulated Fund per category. */
public class OpeningBalanceService {
    private static final String ACCUMULATED_FUND = "ACCUMULATED_FUND";

    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final FinanceSettings financeSettings;

    public OpeningBalanceService() {
        this(new JournalService(), new JournalRepository(), new AccountRepository(), new CollectionCategoryRepository(),
                new FinanceSettings());
    }

    public OpeningBalanceService(JournalService journalService, JournalRepository journalRepository,
                                 AccountRepository accountRepository, CollectionCategoryRepository categoryRepository,
                                 FinanceSettings financeSettings) {
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.financeSettings = financeSettings;
    }

    public OpeningBalanceForm load() {
        try {
            List<Account> balanceAccounts = accountRepository.findAll().stream()
                    .filter(account -> !account.group() && account.isActive())
                    .filter(account -> account.type() == AccountType.ASSET || account.type() == AccountType.LIABILITY)
                    .toList();
            Account fund = accumulatedFund();
            Map<Long, Account> accountsById = accountRepository.findAll().stream()
                    .collect(Collectors.toMap(Account::id, Function.identity()));
            Optional<JournalEntry> entry = journalRepository.findLiveOpeningEntry();
            Map<Long, BigDecimal> accountAmounts = new HashMap<>();
            Map<Long, BigDecimal> fundAmounts = new HashMap<>();
            entry.ifPresent(found -> {
                for (JournalLine line : found.lines()) {
                    BigDecimal netDebit = line.debit().subtract(line.credit());
                    if (line.accountId() == fund.id()) {
                        fundAmounts.merge(line.categoryId(), netDebit.negate(), BigDecimal::add);
                    } else {
                        Account account = accountsById.get(line.accountId());
                        BigDecimal amount = account != null && account.type().isDebitNormal()
                                ? netDebit : netDebit.negate();
                        accountAmounts.merge(line.accountId(), amount, BigDecimal::add);
                    }
                }
            });
            return new OpeningBalanceForm(financeSettings.goLiveDate(), balanceAccounts, categoryRepository.findActive(),
                    accountAmounts, fundAmounts, entry.orElse(null));
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the opening balances right now.", exception);
        }
    }

    public JournalEntry saveDraft(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts) {
        try {
            Optional<JournalEntry> live = journalRepository.findLiveOpeningEntry();
            if (live.isPresent() && live.get().isPosted()) {
                throw new FinanceException("Opening balances are already posted (" + live.get().entryNo()
                        + "). Reverse that entry to correct them.");
            }
            Map<Long, Account> accounts = accountRepository.findAll().stream()
                    .collect(Collectors.toMap(Account::id, Function.identity()));
            List<JournalLineRequest> lines = buildLines(accountAmounts, fundAmounts, accounts, accumulatedFund().id());
            return journalService.saveDraft(new JournalEntryRequest(live.map(JournalEntry::id).orElse(null),
                    JournalSourceType.OPENING, financeSettings.goLiveDate(),
                    "Opening balances at " + financeSettings.goLiveDate(), lines));
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the opening balances right now.", exception);
        }
    }

    public JournalEntry post() {
        try {
            JournalEntry live = journalRepository.findLiveOpeningEntry()
                    .orElseThrow(() -> new FinanceException("Save the opening balances before posting them."));
            if (live.isPosted()) {
                throw new FinanceException("Opening balances are already posted (" + live.entryNo() + ").");
            }
            return journalService.post(live.id());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the opening balances right now.", exception);
        }
    }

    /** Positive amounts sit on the account's normal side; negative ones on the other side. Zero is skipped. */
    static List<JournalLineRequest> buildLines(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts,
                                               Map<Long, Account> accounts, long accumulatedFundId) {
        List<JournalLineRequest> lines = new ArrayList<>();
        accountAmounts.forEach((accountId, amount) -> {
            Account account = accounts.get(accountId);
            if (amount != null && amount.signum() != 0 && account != null) {
                lines.add(line(accountId, amount, account.type().isDebitNormal(), null));
            }
        });
        fundAmounts.forEach((categoryId, amount) -> {
            if (amount != null && amount.signum() != 0) {
                lines.add(line(accumulatedFundId, amount, false, categoryId));
            }
        });
        return lines;
    }

    private static JournalLineRequest line(long accountId, BigDecimal amount, boolean debitNormal, Long categoryId) {
        boolean debit = debitNormal == (amount.signum() > 0);
        BigDecimal value = amount.abs();
        return new JournalLineRequest(accountId, debit ? value : null, debit ? null : value, categoryId, null,
                categoryId == null ? null : "Accumulated Fund");
    }

    private Account accumulatedFund() {
        return accountRepository.findBySystemKey(ACCUMULATED_FUND)
                .orElseThrow(() -> new FinanceException("The Accumulated Fund account is missing from the chart."));
    }
}
