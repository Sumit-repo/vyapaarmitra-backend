package com.vyapaarmitra.api.dataimport;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitContact;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitEntry;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitImportRequest;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitResponse;
import com.vyapaarmitra.api.dataimport.ImportDtos.ImportContactView;
import com.vyapaarmitra.api.dataimport.ImportDtos.ImportEntryView;
import com.vyapaarmitra.api.dataimport.ImportDtos.ParsedStatementView;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedContact;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedEntry;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedStatement;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntry;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.ledger.LedgerService;
import com.vyapaarmitra.api.supplier.Supplier;
import com.vyapaarmitra.api.supplier.SupplierLedgerEntry;
import com.vyapaarmitra.api.supplier.SupplierLedgerEntryRepository;
import com.vyapaarmitra.api.supplier.SupplierRepository;
import com.vyapaarmitra.api.supplier.SupplierService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statement import: parse a PDF into a preview, then commit the (possibly edited)
 * preview into real parties and ledger rows. Stateless between the two calls —
 * the parsed JSON is the only state, so nothing to expire or clean up.
 *
 * <p>Import is free for all plans (no entitlement, no import-specific cap); the
 * only ceilings here are technical abuse guards, and imported rows are excluded
 * from daily-entry usage so a one-time backfill doesn't burn the plan cap.
 */
@Service
public class ImportService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ENTRIES_PER_COMMIT = 5000;
    private static final int MAX_CONTACTS_PER_COMMIT = 1000;

    private final OkCreditStatementParser parser;
    private final BranchAccessService branchAccessService;
    private final LedgerService ledgerService;
    private final SupplierService supplierService;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final ImportBatchRepository importBatchRepository;
    private final AppTime appTime;

    public ImportService(OkCreditStatementParser parser,
                         BranchAccessService branchAccessService,
                         LedgerService ledgerService,
                         SupplierService supplierService,
                         CustomerRepository customerRepository,
                         SupplierRepository supplierRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         SupplierLedgerEntryRepository supplierLedgerEntryRepository,
                         ImportBatchRepository importBatchRepository,
                         AppTime appTime) {
        this.parser = parser;
        this.branchAccessService = branchAccessService;
        this.ledgerService = ledgerService;
        this.supplierService = supplierService;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.supplierLedgerEntryRepository = supplierLedgerEntryRepository;
        this.importBatchRepository = importBatchRepository;
        this.appTime = appTime;
    }

    /**
     * {@code POST /imports/parse}: detect the statement kind, extract contacts and
     * transactions, flag which contacts already exist in scope and how many of their
     * rows would collide with existing ledger rows in the same window. Advisory only —
     * commit never skips a flagged entry.
     */
    public ParsedStatementView parse(AuthUser authUser, byte[] file, String fileName, UUID branchId) {
        if (file == null || file.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "Attach the statement PDF to import.");
        }
        if (file.length > MAX_FILE_BYTES) {
            throw ApiException.unprocessable("IMPORT_TOO_LARGE",
                "Statement is too large. Please use one under 10 MB.");
        }
        if (!looksLikePdf(file)) {
            throw ApiException.unprocessable("UNSUPPORTED_STATEMENT",
                "Only OkCredit statement PDFs can be imported.");
        }

        ParsedStatement parsed;
        try {
            parsed = parser.parse(file);
        } catch (OkCreditStatementParser.UnparseableStatement e) {
            throw ApiException.unprocessable(e.getCode(), e.getMessage());
        }

        // Matching scope: the chosen branch when given, otherwise every branch the user
        // can access (business-wide) — mirrors how the parties list is scoped.
        var scope = branchAccessService.scope(authUser, branchId);

        List<ImportContactView> contacts = new ArrayList<>(parsed.contacts().size());
        for (ParsedContact contact : parsed.contacts()) {
            UUID existingPartyId = matchParty(parsed.kind(), scope, contact.name(), contact.phone())
                .map(ImportService::partyId).orElse(null);
            long duplicates = existingPartyId == null ? 0
                : countDuplicates(parsed.kind(), existingPartyId, contact, parsed.dateFrom(), parsed.dateTo());
            contacts.add(new ImportContactView(contact.name(), contact.phone(), existingPartyId,
                total(contact.entries(), EntryType.CREDIT), total(contact.entries(), EntryType.PAYMENT),
                duplicates, contact.entries().stream()
                    .map(e -> new ImportEntryView(e.date(), e.direction(), e.amount(), e.note()))
                    .toList()));
        }

        return new ParsedStatementView(ImportSource.OKCREDIT, parsed.kind(), parsed.dateFrom(),
            parsed.dateTo(), parsed.netBalance(), parsed.creditCount(), parsed.paymentCount(),
            parsed.warnings(), contacts);
    }

    private static boolean looksLikePdf(byte[] file) {
        return file.length > 4 && file[0] == '%' && file[1] == 'P' && file[2] == 'D' && file[3] == 'F';
    }

    private static UUID partyId(Object party) {
        return party instanceof Customer customer ? customer.getId() : ((Supplier) party).getId();
    }

    private static BigDecimal total(Iterable<OkCreditStatementParser.ParsedEntry> entries,
                                    EntryType direction) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OkCreditStatementParser.ParsedEntry entry : entries) {
            if (entry.direction() == direction) {
                sum = sum.add(entry.amount());
            }
        }
        return sum;
    }

    /**
     * Existing-party match: normalized phone first (exact, then the bare 10-digit tail —
     * OkCredit prints "91"-prefixed numbers), then case-insensitive name.
     */
    private Optional<?> matchParty(ImportKind kind, java.util.Set<UUID> branchIds, String name,
                                   String phone) {
        return kind == ImportKind.CUSTOMER
            ? matchCustomer(branchIds, name, phone)
            : matchSupplier(branchIds, name, phone);
    }

    private Optional<Customer> matchCustomer(java.util.Set<UUID> branchIds, String name, String phone) {
        String normalized = normalizePhone(phone);
        if (normalized != null) {
            var byPhone = customerRepository.findByBranchIdInAndPhone(branchIds, normalized);
            if (!byPhone.isEmpty()) {
                return Optional.of(pick(byPhone));
            }
            var byTail = customerRepository.findByBranchIdInAndPhoneEndingWith(branchIds, phoneTail(normalized));
            if (!byTail.isEmpty()) {
                return Optional.of(pick(byTail));
            }
        }
        var byName = customerRepository.findByBranchIdInAndNameIgnoreCase(branchIds, name.trim());
        return byName.isEmpty() ? Optional.empty() : Optional.of(pick(byName));
    }

    private Optional<Supplier> matchSupplier(java.util.Set<UUID> branchIds, String name, String phone) {
        String normalized = normalizePhone(phone);
        if (normalized != null) {
            var byPhone = supplierRepository.findByBranchIdInAndPhone(branchIds, normalized);
            if (!byPhone.isEmpty()) {
                return Optional.of(pick(byPhone));
            }
            var byTail = supplierRepository.findByBranchIdInAndPhoneEndingWith(branchIds, phoneTail(normalized));
            if (!byTail.isEmpty()) {
                return Optional.of(pick(byTail));
            }
        }
        var byName = supplierRepository.findByBranchIdInAndNameIgnoreCase(branchIds, name.trim());
        return byName.isEmpty() ? Optional.empty() : Optional.of(pick(byName));
    }

    /** Deterministic pick when several parties share a phone/name (rare): name, then id. */
    private static <T> T pick(List<T> parties) {
        Comparator<T> byName = Comparator.comparing(party -> switch (party) {
            case Customer customer -> customer.getName();
            case Supplier supplier -> supplier.getName();
            default -> "";
        });
        return parties.stream().min(byName.thenComparing(party -> switch (party) {
            case Customer customer -> customer.getId().toString();
            case Supplier supplier -> supplier.getId().toString();
            default -> "";
        })).orElseThrow();
    }

    private String normalizePhone(String phone) {
        // Mirrors CustomerService/SupplierService.normalizePhone: separators stripped, "+" kept.
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[\\s-]", "");
    }

    /** Last 10 digits of an already-normalized phone, or null when shorter (non-Indian numbers). */
    private static String phoneTail(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.length() < 10) {
            return null;
        }
        return normalizedPhone.substring(normalizedPhone.length() - 10);
    }

    /**
     * Advisory duplicate count for a matched party: existing rows inside the statement
     * window that collide with a parsed entry on (entryAt, entryType, amount, note).
     * Imported rows land at start-of-day, so this is what catches re-importing the same
     * statement — hand-entered rows (entryAt = now) can't collide and are never flagged.
     */
    private long countDuplicates(ImportKind kind, UUID partyId, ParsedContact contact,
                                 LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            return 0;
        }
        Instant from = appTime.startOfDay(dateFrom);
        Instant to = appTime.startOfDay(dateTo.plusDays(1));
        if (kind == ImportKind.CUSTOMER) {
            List<LedgerEntry> existing = ledgerEntryRepository
                .findByCustomerIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(partyId, from, to);
            return contact.entries().stream().filter(entry -> matchesCustomer(existing, entry)).count();
        }
        List<SupplierLedgerEntry> existing = supplierLedgerEntryRepository
            .findBySupplierIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(partyId, from, to);
        return contact.entries().stream().filter(entry -> matchesSupplier(existing, entry)).count();
    }

    private boolean matchesCustomer(List<LedgerEntry> existing,
                                    OkCreditStatementParser.ParsedEntry entry) {
        Instant entryAt = appTime.startOfDay(entry.date());
        return existing.stream().anyMatch(row -> row.getEntryType() == entry.direction()
            && row.getAmount().compareTo(entry.amount()) == 0
            && Objects.equals(row.getNote(), entry.note())
            && row.getEntryAt().equals(entryAt));
    }

    private boolean matchesSupplier(List<SupplierLedgerEntry> existing,
                                    OkCreditStatementParser.ParsedEntry entry) {
        Instant entryAt = appTime.startOfDay(entry.date());
        return existing.stream().anyMatch(row -> row.getEntryType() == entry.direction()
            && row.getAmount().compareTo(entry.amount()) == 0
            && Objects.equals(row.getNote(), entry.note())
            && row.getEntryAt().equals(entryAt));
    }

    /**
     * {@code POST /imports/commit}: resolve-or-create a party per contact, then replay
     * the parsed entries through the ordinary ledger create path so balances, trust
     * scores and defaulter flags end up exactly as if they'd been hand-entered.
     */
    @Transactional
    public CommitResponse commit(AuthUser authUser, CommitImportRequest request) {
        branchAccessService.assertBranchAccess(authUser, request.branchId());
        if (request.source() != ImportSource.OKCREDIT) {
            throw ApiException.badRequest("UNSUPPORTED_SOURCE", "Only OkCredit statements can be imported.");
        }
        if (request.contacts().size() > MAX_CONTACTS_PER_COMMIT) {
            throw ApiException.badRequest("IMPORT_TOO_LARGE", "An import is limited to "
                + MAX_CONTACTS_PER_COMMIT + " contacts.");
        }
        int totalEntries = request.contacts().stream().mapToInt(contact -> contact.entries().size()).sum();
        if (totalEntries > MAX_ENTRIES_PER_COMMIT) {
            throw ApiException.badRequest("IMPORT_TOO_LARGE", "An import is limited to "
                + MAX_ENTRIES_PER_COMMIT + " transactions.");
        }
        LocalDate today = appTime.today();
        for (CommitContact contact : request.contacts()) {
            for (CommitEntry entry : contact.entries()) {
                if (entry.date().isAfter(today)) {
                    throw ApiException.badRequest("INVALID_ENTRY_DATE",
                        "Transactions cannot be dated in the future (" + entry.date() + ").");
                }
            }
        }

        ImportBatch batch = new ImportBatch();
        batch.setBusinessId(authUser.businessId());
        batch.setKind(request.kind());
        batch.setSource(request.source());
        batch.setCreatedBy(authUser.id());
        batch = importBatchRepository.save(batch);

        int partiesCreated = 0;
        int partiesMatched = 0;
        int entriesCreated = 0;
        for (CommitContact contact : request.contacts()) {
            if (request.kind() == ImportKind.CUSTOMER) {
                Resolution<Customer> resolution = resolveCustomer(authUser, request.branchId(), contact);
                partiesCreated += resolution.created() ? 1 : 0;
                partiesMatched += resolution.created() ? 0 : 1;
                for (CommitEntry entry : sorted(contact.entries())) {
                    ledgerService.createEntry(resolution.party(), entry.direction(), entry.amount(),
                        entry.note(), entry.date(), batch.getId(), authUser.id());
                    entriesCreated++;
                }
            } else {
                Resolution<Supplier> resolution = resolveSupplier(authUser, request.branchId(), contact);
                partiesCreated += resolution.created() ? 1 : 0;
                partiesMatched += resolution.created() ? 0 : 1;
                for (CommitEntry entry : sorted(contact.entries())) {
                    supplierService.createEntry(resolution.party(), entry.direction(), entry.amount(),
                        entry.note(), entry.date(), batch.getId(), authUser.id());
                    entriesCreated++;
                }
            }
        }

        batch.setPartiesCreated(partiesCreated);
        batch.setEntriesCreated(entriesCreated);
        importBatchRepository.save(batch);

        return new CommitResponse(batch.getId(), partiesCreated, partiesMatched, entriesCreated);
    }

    /** A party found in scope, or a freshly created one — with which of the two happened. */
    private record Resolution<T>(T party, boolean created) {
    }

    private static List<CommitEntry> sorted(List<CommitEntry> entries) {
        return entries.stream().sorted(java.util.Comparator.comparing(CommitEntry::date)).toList();
    }

    private Resolution<Customer> resolveCustomer(AuthUser authUser, UUID branchId,
                                                 CommitContact contact) {
        Optional<Customer> matched = matchCustomer(branchAccessService.accessibleBranchIds(authUser),
            contact.name(), contact.phone());
        return matched.map(party -> new Resolution<>(party, false)).orElseGet(() -> {
            Customer customer = new Customer();
            customer.setBusinessId(authUser.businessId());
            customer.setBranchId(branchId);
            customer.setName(contact.name().trim());
            customer.setPhone(normalizePhone(contact.phone()));
            return new Resolution<>(customerRepository.save(customer), true);
        });
    }

    private Resolution<Supplier> resolveSupplier(AuthUser authUser, UUID branchId,
                                                 CommitContact contact) {
        Optional<Supplier> matched = matchSupplier(branchAccessService.accessibleBranchIds(authUser),
            contact.name(), contact.phone());
        return matched.map(party -> new Resolution<>(party, false)).orElseGet(() -> {
            Supplier supplier = new Supplier();
            supplier.setBusinessId(authUser.businessId());
            supplier.setBranchId(branchId);
            supplier.setName(contact.name().trim());
            supplier.setPhone(normalizePhone(contact.phone()));
            return new Resolution<>(supplierRepository.save(supplier), true);
        });
    }
}