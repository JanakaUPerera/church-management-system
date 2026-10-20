package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.validation.JournalValidator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The only way journal entries are saved, posted and reversed. */
public class JournalService {
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final FiscalYearService fiscalYearService;
    private final FinanceSettings financeSettings;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public JournalService() {
        this(new JournalRepository(), new AccountRepository(), new CollectionCategoryRepository(),
                new FiscalYearService(), new FinanceSettings(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public JournalService(JournalRepository journalRepository, AccountRepository accountRepository,
                          CollectionCategoryRepository categoryRepository, FiscalYearService fiscalYearService,
                          FinanceSettings financeSettings, ActivityLogService activityLogService, Clock clock) {
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.fiscalYearService = fiscalYearService;
        this.financeSettings = financeSettings;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public JournalEntry saveDraft(JournalEntryRequest request) {
        AuthenticatedUser user = require("finance.entry", "You do not have permission to enter journal entries.");
        if (request.sourceType() != JournalSourceType.JOURNAL && request.sourceType() != JournalSourceType.OPENING) {
            throw new FinanceException("Only journal and opening-balance entries can be saved as drafts.");
        }
        try {
            FiscalYear fiscalYear = validate(request);
            LocalDateTime now = LocalDateTime.now(clock);
            long id;
            if (request.id() == null) {
                id = journalRepository.insertDraft(toEntry(request, fiscalYear.year(), user.getUserId(), now, null));
            } else {
                JournalEntry existing = requireEntry(request.id());
                if (existing.isPosted()) {
                    throw new FinanceException("Posted entries cannot be edited; reverse them instead.");
                }
                if (existing.sourceType() != request.sourceType()) {
                    throw new FinanceException("This draft belongs to a different screen.");
                }
                journalRepository.updateDraft(new JournalEntry(existing.id(), null, request.entryDate(),
                        fiscalYear.year(), request.narration().strip(), existing.sourceType(), existing.sourceId(),
                        JournalStatus.DRAFT, null, existing.preparedBy(), existing.preparedAt(), null, null,
                        toLines(request.lines())));
                id = existing.id();
            }
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_DRAFT_SAVED, String.valueOf(id),
                    request.narration().strip());
            return requireEntry(id);
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the entry right now.", exception);
        }
    }

    public void deleteDraft(long id) {
        AuthenticatedUser user = require("finance.entry", "You do not have permission to delete journal entries.");
        try {
            JournalEntry existing = requireEntry(id);
            if (existing.isPosted()) {
                throw new FinanceException("Posted entries cannot be deleted; reverse them instead.");
            }
            journalRepository.deleteDraft(id);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_DRAFT_DELETED,
                    String.valueOf(id), existing.narration());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to delete the entry right now.", exception);
        }
    }

    public JournalEntry post(long id) {
        AuthenticatedUser user = currentUser("You do not have permission to post journal entries.");
        try {
            JournalEntry entry = requireEntry(id);
            if (entry.isPosted()) {
                throw new FinanceException("This entry is already posted.");
            }
            requirePostPermission(user, entry);
            validate(toRequest(entry));
            String entryNo = journalRepository.post(id, user.getUserId(), LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_POSTED, String.valueOf(id),
                    entryNo + " " + entry.narration());
            return requireEntry(id);
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the entry right now.", exception);
        }
    }

    public JournalEntry reverse(long id, LocalDate date, String reason) {
        AuthenticatedUser user = financeSettings.approvalRequired()
                ? require("finance.approve", "You do not have permission to reverse entries.")
                : require("finance.entry", "You do not have permission to reverse entries.");
        if (reason == null || reason.isBlank()) {
            throw new FinanceException("Enter the reason for the reversal.");
        }
        try {
            JournalEntry original = requireEntry(id);
            if (!original.isPosted()) {
                throw new FinanceException("Only posted entries can be reversed; delete the draft instead.");
            }
            if (original.sourceType() == JournalSourceType.REVERSAL) {
                throw new FinanceException("A reversal cannot be reversed; enter a new entry instead.");
            }
            if (journalRepository.isReversed(id)) {
                throw new FinanceException(original.entryNo() + " has already been reversed.");
            }
            if (date == null || date.isBefore(original.entryDate())) {
                throw new FinanceException("The reversal date cannot be before " + original.entryDate() + ".");
            }
            List<JournalLineRequest> swapped = original.lines().stream()
                    .map(line -> new JournalLineRequest(line.accountId(), line.credit(), line.debit(),
                            line.categoryId(), line.churchId(), line.memo()))
                    .toList();
            JournalEntryRequest request = new JournalEntryRequest(null, JournalSourceType.REVERSAL, date,
                    "Reversal of " + original.entryNo() + " — " + reason.strip(), swapped);
            FiscalYear fiscalYear = validate(request);
            LocalDateTime now = LocalDateTime.now(clock);
            PostedEntry posted = journalRepository.insertPosted(
                    toEntry(request, fiscalYear.year(), user.getUserId(), now, original.id()), user.getUserId(), now);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_REVERSED,
                    String.valueOf(original.id()), posted.entryNo() + " reverses " + original.entryNo());
            return requireEntry(posted.id());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to reverse the entry right now.", exception);
        }
    }

    public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
        require("finance.menu.view", "You do not have permission to view journal entries.");
        try {
            return journalRepository.search(criteria);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load journal entries right now.", exception);
        }
    }

    public Optional<JournalEntry> find(long id) {
        require("finance.menu.view", "You do not have permission to view journal entries.");
        try {
            return journalRepository.findById(id);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the entry right now.", exception);
        }
    }

    public AccountLedger ledger(long accountId, LocalDate from, LocalDate to) {
        require("finance.menu.view", "You do not have permission to view the ledger.");
        if (from == null || to == null || to.isBefore(from)) {
            throw new FinanceException("Choose a valid date range.");
        }
        try {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new FinanceException("The account could not be found."));
            boolean debitNormal = account.type().isDebitNormal();
            BigDecimal opening = normal(journalRepository.netDebitBefore(accountId, from), debitNormal);
            BigDecimal running = opening;
            List<AccountLedger.Row> rows = new ArrayList<>();
            for (LedgerLine line : journalRepository.ledgerLines(accountId, from, to)) {
                running = running.add(normal(line.debit().subtract(line.credit()), debitNormal));
                rows.add(new AccountLedger.Row(line, running));
            }
            return new AccountLedger(account, opening, rows, running);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the ledger right now.", exception);
        }
    }

    /** Whether the signed-in user may post this draft under the current approval setting. */
    public boolean canPost(JournalEntry entry) {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty() || entry.isPosted()) {
            return false;
        }
        PermissionGuard guard = new PermissionGuard(user.get());
        if (financeSettings.approvalRequired()) {
            return guard.can("finance.approve") && !Long.valueOf(user.get().getUserId()).equals(entry.preparedBy());
        }
        return guard.can("finance.entry");
    }

    public boolean canReverse(JournalEntry entry) {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty() || !entry.isPosted() || entry.sourceType() == JournalSourceType.REVERSAL) {
            return false;
        }
        return new PermissionGuard(user.get()).can(financeSettings.approvalRequired() ? "finance.approve" : "finance.entry");
    }

    /** For F2/F3 documents: validates and posts in one step (the document carries its own approval). */
    JournalEntry postSystemEntry(JournalEntryRequest request, JournalSourceType sourceType, Long sourceId) {
        AuthenticatedUser user = currentUser("Please sign in.");
        try {
            JournalEntryRequest typed = new JournalEntryRequest(null, sourceType, request.entryDate(),
                    request.narration(), request.lines());
            FiscalYear fiscalYear = validate(typed);
            LocalDateTime now = LocalDateTime.now(clock);
            JournalEntry entry = toEntry(typed, fiscalYear.year(), user.getUserId(), now, null);
            JournalEntry withSource = new JournalEntry(null, null, entry.entryDate(), entry.fiscalYear(),
                    entry.narration(), sourceType, sourceId, JournalStatus.DRAFT, null, entry.preparedBy(),
                    entry.preparedAt(), null, null, entry.lines());
            PostedEntry posted = journalRepository.insertPosted(withSource, user.getUserId(), now);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_POSTED,
                    String.valueOf(posted.id()), posted.entryNo() + " " + sourceType);
            return requireEntry(posted.id());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the entry right now.", exception);
        }
    }

    private FiscalYear validate(JournalEntryRequest request) {
        Map<Long, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));
        Set<Long> categoryIds = categoryRepository.findActive().stream()
                .map(CollectionCategory::getId)
                .collect(Collectors.toSet());
        JournalValidator.validate(request, accounts, categoryIds, financeSettings.goLiveDate());
        return fiscalYearService.requireOpen(request.entryDate());
    }

    private void requirePostPermission(AuthenticatedUser user, JournalEntry entry) {
        PermissionGuard guard = new PermissionGuard(user);
        if (financeSettings.approvalRequired()) {
            if (!guard.can("finance.approve")) {
                throw new FinanceException("Approval is required; ask an approver to post this entry.");
            }
            if (Long.valueOf(user.getUserId()).equals(entry.preparedBy())) {
                throw new FinanceException("You prepared this entry; another approver must post it.");
            }
        } else if (!guard.can("finance.entry")) {
            throw new FinanceException("You do not have permission to post journal entries.");
        }
    }

    private JournalEntry requireEntry(long id) {
        return journalRepository.findById(id).orElseThrow(() -> new FinanceException("The entry could not be found."));
    }

    private static JournalEntry toEntry(JournalEntryRequest request, int fiscalYear, long userId, LocalDateTime now,
                                        Long reversalOfId) {
        return new JournalEntry(null, null, request.entryDate(), fiscalYear, request.narration().strip(),
                request.sourceType(), null, JournalStatus.DRAFT, reversalOfId, userId, now, null, null,
                toLines(request.lines()));
    }

    private static List<JournalLine> toLines(List<JournalLineRequest> lines) {
        List<JournalLine> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            JournalLineRequest line = lines.get(index);
            result.add(new JournalLine(null, index + 1, line.accountId(), line.debitOrZero(), line.creditOrZero(),
                    line.categoryId(), line.churchId(),
                    line.memo() == null || line.memo().isBlank() ? null : line.memo().strip()));
        }
        return result;
    }

    private static JournalEntryRequest toRequest(JournalEntry entry) {
        return new JournalEntryRequest(entry.id(), entry.sourceType(), entry.entryDate(), entry.narration(),
                entry.lines().stream()
                        .map(line -> new JournalLineRequest(line.accountId(), line.debit(), line.credit(),
                                line.categoryId(), line.churchId(), line.memo()))
                        .toList());
    }

    private static BigDecimal normal(BigDecimal netDebit, boolean debitNormal) {
        return debitNormal ? netDebit : netDebit.negate();
    }

    private static AuthenticatedUser currentUser(String message) {
        return AuthContext.getCurrentUser().orElseThrow(() -> new FinanceException(message));
    }

    private static AuthenticatedUser require(String permission, String message) {
        AuthenticatedUser user = currentUser(message);
        if (!new PermissionGuard(user).can(permission)) {
            throw new FinanceException(message);
        }
        return user;
    }
}
