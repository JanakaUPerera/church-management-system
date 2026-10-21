package com.churchmanagement.testsupport;

import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.FiscalYearRepository;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.FinanceSettings;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fakes for the finance services' tests. */
public final class FinanceFakes {
    private FinanceFakes() {
    }

    public static Account account(long id, String code, String name, AccountType type, Long parentId, boolean group) {
        return new Account(id, code, name, type, parentId, group, CashFlowClass.OPERATING, null, null,
                Account.Status.ACTIVE);
    }

    public static CollectionCategory category(long id, String code) {
        return new CollectionCategory(id, code, "Category " + code, null, CollectionCategory.Status.ACTIVE, null, null);
    }

    public static AuthenticatedUser user(long id, String... permissions) {
        return new AuthenticatedUser(id, "user" + id, "User " + id, 2L, "Clerk", List.of(permissions));
    }

    public static class FakeAccountRepository extends AccountRepository {
        public final Map<Long, Account> accounts = new LinkedHashMap<>();
        public final Map<Long, BigDecimal> netDebits = new HashMap<>();
        public final List<Long> usedIds = new ArrayList<>();
        public final List<Long> draftIds = new ArrayList<>();
        public final Map<Long, Integer> openYearUsage = new HashMap<>();
        private long nextId = 1000;

        public FakeAccountRepository() {
            super((DataSource) null);
        }

        public FakeAccountRepository with(Account account) {
            accounts.put(account.id(), account);
            return this;
        }

        @Override
        public List<Account> findAll() {
            return accounts.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<Account> findByCode(String code) {
            return accounts.values().stream().filter(account -> account.code().equals(code)).findFirst();
        }

        @Override
        public Optional<Account> findBySystemKey(String systemKey) {
            return accounts.values().stream().filter(account -> systemKey.equals(account.systemKey())).findFirst();
        }

        @Override
        public Account insert(Account account, LocalDateTime now) {
            Account saved = account.withId(nextId++);
            accounts.put(saved.id(), saved);
            return saved;
        }

        @Override
        public void update(Account account, LocalDateTime now) {
            accounts.put(account.id(), account);
        }

        @Override
        public void updateStatus(long id, Account.Status status, LocalDateTime now) {
            accounts.put(id, accounts.get(id).withStatus(status));
        }

        @Override
        public void delete(long id) {
            accounts.remove(id);
        }

        @Override
        public boolean isUsed(long id) {
            return usedIds.contains(id);
        }

        @Override
        public boolean hasChildren(long id) {
            return accounts.values().stream().anyMatch(account -> Long.valueOf(id).equals(account.parentId()));
        }

        @Override
        public boolean hasActiveChildren(long id) {
            return accounts.values().stream()
                    .anyMatch(account -> Long.valueOf(id).equals(account.parentId()) && account.isActive());
        }

        @Override
        public Map<Long, BigDecimal> postedNetDebits() {
            return netDebits;
        }

        @Override
        public BigDecimal postedNetDebit(long id) {
            return netDebits.getOrDefault(id, BigDecimal.ZERO);
        }

        @Override
        public boolean hasDraftLines(long id) {
            return draftIds.contains(id);
        }

        @Override
        public Optional<Integer> openYearWithPostedLines(long id) {
            return Optional.ofNullable(openYearUsage.get(id));
        }
    }

    public static class FakeJournalRepository extends JournalRepository {
        public final Map<Long, JournalEntry> entries = new LinkedHashMap<>();
        public final List<LedgerLine> ledger = new ArrayList<>();
        public BigDecimal openingNetDebit = BigDecimal.ZERO;
        private final Map<Integer, Integer> numbers = new HashMap<>();
        private long nextId = 1;

        public FakeJournalRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<JournalEntry> findById(long id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
            return List.of();
        }

        @Override
        public long insertDraft(JournalEntry entry) {
            long id = nextId++;
            entries.put(id, copy(entry, id, null, JournalStatus.DRAFT, null, null));
            return id;
        }

        @Override
        public void updateDraft(JournalEntry entry) {
            JournalEntry existing = entries.get(entry.id());
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be edited.");
            }
            entries.put(entry.id(), copy(entry, entry.id(), null, JournalStatus.DRAFT, null, null));
        }

        @Override
        public void deleteDraft(long id) {
            JournalEntry existing = entries.get(id);
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be deleted.");
            }
            entries.remove(id);
        }

        @Override
        public String post(long id, long postedBy, LocalDateTime postedAt) {
            JournalEntry existing = entries.get(id);
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be posted.");
            }
            String entryNo = nextNumber(existing.fiscalYear());
            entries.put(id, copy(existing, id, entryNo, JournalStatus.POSTED, postedBy, postedAt));
            return entryNo;
        }

        @Override
        public PostedEntry insertPosted(JournalEntry entry, long postedBy, LocalDateTime postedAt) {
            long id = nextId++;
            String entryNo = nextNumber(entry.fiscalYear());
            entries.put(id, copy(entry, id, entryNo, JournalStatus.POSTED, postedBy, postedAt));
            return new PostedEntry(id, entryNo);
        }

        @Override
        public boolean isReversed(long id) {
            return entries.values().stream().anyMatch(entry -> Long.valueOf(id).equals(entry.reversalOfId()));
        }

        @Override
        public boolean hasPostedEntries() {
            return entries.values().stream().anyMatch(JournalEntry::isPosted);
        }

        @Override
        public Optional<JournalEntry> findLiveOpeningEntry() {
            return entries.values().stream()
                    .filter(entry -> entry.sourceType() == JournalSourceType.OPENING)
                    .filter(entry -> !isReversed(entry.id()))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<LedgerLine> ledgerLines(long accountId, LocalDate from, LocalDate to) {
            return ledger;
        }

        @Override
        public BigDecimal netDebitBefore(long accountId, LocalDate date) {
            return openingNetDebit;
        }

        private String nextNumber(int year) {
            int number = numbers.merge(year, 1, Integer::sum);
            return JournalRepository.formatEntryNo(year, number);
        }

        private static JournalEntry copy(JournalEntry entry, long id, String entryNo, JournalStatus status,
                                         Long postedBy, LocalDateTime postedAt) {
            return new JournalEntry(id, entryNo, entry.entryDate(), entry.fiscalYear(), entry.narration(),
                    entry.sourceType(), entry.sourceId(), status, entry.reversalOfId(), entry.preparedBy(),
                    entry.preparedAt(), postedBy, postedAt, entry.lines());
        }
    }

    public static class FakeFiscalYearRepository extends FiscalYearRepository {
        private final Map<Integer, FiscalYear> years = new HashMap<>();

        public FakeFiscalYearRepository() {
            super((DataSource) null);
        }

        public void close(int year) {
            FiscalYear open = FiscalYear.open(year);
            years.put(year, new FiscalYear(year, open.startDate(), open.endDate(), FiscalYear.Status.CLOSED));
        }

        @Override
        public Optional<FiscalYear> findByYear(int year) {
            return Optional.ofNullable(years.get(year));
        }

        @Override
        public void insertIfMissing(FiscalYear fiscalYear) {
            years.putIfAbsent(fiscalYear.year(), fiscalYear);
        }
    }

    public static class FakeCategoryRepository extends CollectionCategoryRepository {
        public final List<CollectionCategory> active = new ArrayList<>();

        public FakeCategoryRepository() {
            super((DataSource) null);
        }

        @Override
        public List<CollectionCategory> findActive() {
            return active;
        }
    }

    public static class FakeFinanceSettings extends FinanceSettings {
        public LocalDate goLiveDate = LocalDate.of(2027, 1, 1);
        public boolean approvalRequired;

        public FakeFinanceSettings() {
            super(null);
        }

        @Override
        public LocalDate goLiveDate() {
            return goLiveDate;
        }

        @Override
        public boolean approvalRequired() {
            return approvalRequired;
        }
    }

    public static class RecordingActivityLog extends ActivityLogService {
        public final List<String> actions = new ArrayList<>();

        public RecordingActivityLog() {
            super(null);
        }

        @Override
        public void logFinance(Long userId, String action, String recordId, String details) {
            actions.add(action);
        }
    }
}
