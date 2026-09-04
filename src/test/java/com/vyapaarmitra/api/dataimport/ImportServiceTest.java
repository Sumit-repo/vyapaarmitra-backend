package com.vyapaarmitra.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedContact;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedEntry;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedStatement;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.UnparseableStatement;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntry;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.ledger.LedgerService;
import com.vyapaarmitra.api.supplier.Supplier;
import com.vyapaarmitra.api.supplier.SupplierLedgerEntryRepository;
import com.vyapaarmitra.api.supplier.SupplierRepository;
import com.vyapaarmitra.api.supplier.SupplierService;
import com.vyapaarmitra.api.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Commit flow: party match/create, ledger replay, ceilings, and the parse-side flags. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private final AuthUser authUser = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);
    // Shared by the static request builders below.
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock private OkCreditStatementParser parser;
    @Mock private BranchAccessService branchAccessService;
    @Mock private LedgerService ledgerService;
    @Mock private SupplierService supplierService;
    @Mock private CustomerRepository customerRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    @Mock private ImportBatchRepository importBatchRepository;
    @Mock private AppTime appTime;

    @InjectMocks private ImportService service;

    private void stubTime() {
        when(appTime.today()).thenReturn(TODAY);
        when(appTime.startOfDay(any())).thenAnswer(invocation ->
            invocation.getArgument(0, LocalDate.class).atStartOfDay(ZONE).toInstant());
    }

    private void stubScope() {
        when(branchAccessService.accessibleBranchIds(authUser)).thenReturn(Set.of(BRANCH_ID));
    }

    private static Customer customer(String name, String phone) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setName(name);
        customer.setPhone(phone);
        return customer;
    }

    private static ParsedStatement parsedStatement(ImportKind kind) {
        return new ParsedStatement(kind, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
            new BigDecimal("1"), 1, 1,
            List.of(new ParsedContact("Ramesh Kumar", "9122244192", List.of(
                new ParsedEntry(LocalDate.of(2026, 8, 9), EntryType.CREDIT, new BigDecimal("2"),
                    "Items on credit"),
                new ParsedEntry(LocalDate.of(2026, 8, 9), EntryType.PAYMENT, new BigDecimal("1"),
                    "Cash paid")))),
            List.of());
    }

    private static ImportDtos.CommitImportRequest commitRequest(ImportKind kind,
                                                                List<ImportDtos.CommitContact> contacts) {
        return new ImportDtos.CommitImportRequest(ImportSource.OKCREDIT, kind, BRANCH_ID, contacts);
    }

    private static ImportDtos.CommitContact commitContact(String name, String phone) {
        return new ImportDtos.CommitContact(name, phone,
            List.of(new ImportDtos.CommitEntry(LocalDate.of(2026, 8, 9), EntryType.CREDIT,
                new BigDecimal("2"), "Items on credit")));
    }

    // ---------------------------------------------------------------------- commit

    @Test
    void commitCreatesMissingPartiesAndReplaysEntriesThroughTheLedger() {
        stubScope();
        stubTime();
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of());
        when(customerRepository.findByBranchIdInAndNameIgnoreCase(any(), anyString())).thenReturn(List.of());
        Customer saved = customer("Ramesh Kumar", "9122244192");
        when(customerRepository.save(any(Customer.class))).thenReturn(saved);
        ImportBatch batch = new ImportBatch();
        batch.setId(UUID.randomUUID());
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(batch);

        ImportDtos.CommitResponse response = service.commit(authUser,
            commitRequest(ImportKind.CUSTOMER, List.of(commitContact("Ramesh Kumar", "9122244192"))));

        assertThat(response.partiesCreated()).isEqualTo(1);
        assertThat(response.partiesMatched()).isZero();
        assertThat(response.entriesCreated()).isEqualTo(1);
        assertThat(response.batchId()).isEqualTo(batch.getId());

        ArgumentCaptor<Customer> created = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Ramesh Kumar");
        assertThat(created.getValue().getPhone()).isEqualTo("9122244192");
        assertThat(created.getValue().getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(created.getValue().getTrustBucket()).isEqualTo(com.vyapaarmitra.api.customer.TrustBucket.NEW);

        verify(ledgerService).createEntry(saved, EntryType.CREDIT, new BigDecimal("2"),
            "Items on credit", LocalDate.of(2026, 8, 9), batch.getId(), authUser.id());
    }

    @Test
    void commitMatchesExistingPartyByPhoneTailInsteadOfCreating() {
        stubScope();
        stubTime();
        Customer existing = customer("Ramesh", "+919122244192");
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of());
        // OkCredit prints the bare 10-digit number; the stored phone is +91-prefixed.
        when(customerRepository.findByBranchIdInAndPhoneEndingWith(any(), anyString()))
            .thenReturn(List.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(new ImportBatch());

        ImportDtos.CommitResponse response = service.commit(authUser,
            commitRequest(ImportKind.CUSTOMER, List.of(commitContact("Ramesh Kumar", "9122244192"))));

        assertThat(response.partiesCreated()).isZero();
        assertThat(response.partiesMatched()).isEqualTo(1);
        verify(customerRepository, never()).save(any(Customer.class));
        verify(ledgerService).createEntry(existing, EntryType.CREDIT, new BigDecimal("2"),
            "Items on credit", LocalDate.of(2026, 8, 9), response.batchId(), authUser.id());
    }

    @Test
    void commitFallsBackToNameMatchingWhenPhoneIsUnknown() {
        stubScope();
        stubTime();
        Customer existing = customer("Ramesh Kumar", "9999999999");
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of());
        when(customerRepository.findByBranchIdInAndPhoneEndingWith(any(), anyString())).thenReturn(List.of());
        when(customerRepository.findByBranchIdInAndNameIgnoreCase(any(), anyString()))
            .thenReturn(List.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(new ImportBatch());

        ImportDtos.CommitResponse response = service.commit(authUser,
            commitRequest(ImportKind.CUSTOMER, List.of(commitContact("ramesh kumar", "9122244192"))));

        assertThat(response.partiesMatched()).isEqualTo(1);
        verify(ledgerService).createEntry(existing, EntryType.CREDIT, new BigDecimal("2"),
            "Items on credit", LocalDate.of(2026, 8, 9), response.batchId(), authUser.id());
    }

    @Test
    void commitRoutesSupplierStatementsToTheSupplierLedger() {
        stubScope();
        stubTime();
        Supplier existing = new Supplier();
        existing.setId(UUID.randomUUID());
        existing.setName("Rajshree Enterprise");
        when(supplierRepository.findByBranchIdInAndNameIgnoreCase(any(), anyString()))
            .thenReturn(List.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(new ImportBatch());

        ImportDtos.CommitResponse response = service.commit(authUser,
            commitRequest(ImportKind.SUPPLIER, List.of(commitContact("Rajshree Enterprise", null))));

        assertThat(response.partiesMatched()).isEqualTo(1);
        assertThat(response.entriesCreated()).isEqualTo(1);
        verify(supplierService).createEntry(existing, EntryType.CREDIT, new BigDecimal("2"),
            "Items on credit", LocalDate.of(2026, 8, 9), response.batchId(), authUser.id());
        verify(ledgerService, never()).createEntry(any(Customer.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commitRejectsFutureDatedEntries() {
        stubScope();
        stubTime();
        ImportDtos.CommitImportRequest request = new ImportDtos.CommitImportRequest(
            ImportSource.OKCREDIT, ImportKind.CUSTOMER, BRANCH_ID,
            List.of(new ImportDtos.CommitContact("Ramesh", null, List.of(
                new ImportDtos.CommitEntry(TODAY.plusDays(1), EntryType.CREDIT, new BigDecimal("2"), null)))));

        assertThatThrownBy(() -> service.commit(authUser, request))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> {
                assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_ENTRY_DATE");
                assertThat(((ApiException) e).getStatus().value()).isEqualTo(400);
            });
        verify(importBatchRepository, never()).save(any(ImportBatch.class));
        verify(ledgerService, never()).createEntry(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commitRejectsTooManyContacts() {
        stubScope();
        stubTime();
        List<ImportDtos.CommitContact> tooMany = IntStream.rangeClosed(1, 1001)
            .mapToObj(i -> commitContact("Party " + i, null)).toList();

        assertThatThrownBy(() -> service.commit(authUser, commitRequest(ImportKind.CUSTOMER, tooMany)))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("IMPORT_TOO_LARGE"));
        verify(importBatchRepository, never()).save(any(ImportBatch.class));
    }

    // ----------------------------------------------------------------------- parse

    @Test
    void parseFlagsExistingPartyAndCountsDuplicates() {
        stubScope();
        stubTime();
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        when(parser.parse(pdf)).thenReturn(parsedStatement(ImportKind.CUSTOMER));
        Customer existing = customer("Ramesh", "9122244192");
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of(existing));
        when(ledgerEntryRepository.findByCustomerIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(
            any(), any(), any())).thenReturn(List.of(ledgerEntry(EntryType.CREDIT, "2", "Items on credit",
            LocalDate.of(2026, 8, 9))));

        ImportDtos.ParsedStatementView view = service.parse(authUser, pdf, "statement.pdf", null);

        assertThat(view.source()).isEqualTo(ImportSource.OKCREDIT);
        assertThat(view.kind()).isEqualTo(ImportKind.CUSTOMER);
        assertThat(view.dateFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(view.dateTo()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(view.netBalance()).isEqualByComparingTo("1");
        assertThat(view.creditCount()).isEqualTo(1);
        assertThat(view.paymentCount()).isEqualTo(1);

        assertThat(view.contacts()).hasSize(1);
        ImportDtos.ImportContactView contact = view.contacts().get(0);
        assertThat(contact.existingPartyId()).isEqualTo(existing.getId());
        assertThat(contact.creditTotal()).isEqualByComparingTo("2");
        assertThat(contact.paymentTotal()).isEqualByComparingTo("1");
        assertThat(contact.duplicateCount()).isEqualTo(1);
        assertThat(contact.entries()).hasSize(2);
        assertThat(contact.entries().get(0).direction()).isEqualTo(EntryType.CREDIT);
    }

    @Test
    void parseFlagsNoDuplicatesForUnknownParties() {
        stubScope();
        stubTime();
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        when(parser.parse(pdf)).thenReturn(parsedStatement(ImportKind.CUSTOMER));
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of());
        when(customerRepository.findByBranchIdInAndNameIgnoreCase(any(), anyString())).thenReturn(List.of());

        ImportDtos.ParsedStatementView view = service.parse(authUser, pdf, "stmt.pdf", null);

        assertThat(view.contacts().get(0).existingPartyId()).isNull();
        assertThat(view.contacts().get(0).duplicateCount()).isZero();
        verify(ledgerEntryRepository, never())
            .findByCustomerIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(any(), any(), any());
    }

    @Test
    void parseOnlyFlagsRowsMatchingDateTypeAmountAndNote() {
        stubScope();
        stubTime();
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        ParsedStatement parsed = new ParsedStatement(ImportKind.CUSTOMER, LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31), new BigDecimal("2"), 1, 0,
            List.of(new ParsedContact("Ramesh", "9122244192", List.of(
                new ParsedEntry(LocalDate.of(2026, 8, 9), EntryType.CREDIT, new BigDecimal("2"), null)))),
            List.of());
        when(parser.parse(pdf)).thenReturn(parsed);
        Customer existing = customer("Ramesh", "9122244192");
        when(customerRepository.findByBranchIdInAndPhone(any(), anyString())).thenReturn(List.of(existing));
        // Same shape but a different note → not a duplicate. Different day → not a duplicate.
        LedgerEntry differentNote = new LedgerEntry();
        differentNote.setEntryType(EntryType.CREDIT);
        differentNote.setAmount(new BigDecimal("2"));
        differentNote.setNote("Cash paid");
        differentNote.setEntryAt(appTime.startOfDay(LocalDate.of(2026, 8, 9)));
        LedgerEntry differentDay = new LedgerEntry();
        differentDay.setEntryType(EntryType.CREDIT);
        differentDay.setAmount(new BigDecimal("2"));
        differentDay.setEntryAt(appTime.startOfDay(LocalDate.of(2026, 8, 10)));
        when(ledgerEntryRepository.findByCustomerIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(
            any(), any(), any())).thenReturn(List.of(differentNote, differentDay));

        ImportDtos.ParsedStatementView view = service.parse(authUser, pdf, "stmt.pdf", null);

        assertThat(view.contacts().get(0).duplicateCount()).isZero();
    }

    @Test
    void parseRejectsNonPdfPayload() {
        assertThatThrownBy(() -> service.parse(authUser, "just text".getBytes(), "stmt.pdf", null))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> {
                assertThat(((ApiException) e).getCode()).isEqualTo("UNSUPPORTED_STATEMENT");
                assertThat(((ApiException) e).getStatus().value()).isEqualTo(422);
            });
    }

    @Test
    void parseRejectsOversizedPayload() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        assertThatThrownBy(() -> service.parse(authUser, big, "stmt.pdf", null))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> {
                assertThat(((ApiException) e).getCode()).isEqualTo("IMPORT_TOO_LARGE");
                assertThat(((ApiException) e).getStatus().value()).isEqualTo(422);
            });
    }

    @Test
    void parseRejectsEmptyUpload() {
        assertThatThrownBy(() -> service.parse(authUser, new byte[0], "stmt.pdf", null))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("EMPTY_FILE"));
    }

    @Test
    void parseMapsParserRejectionToTheSameErrorCode() {
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        when(parser.parse(pdf)).thenThrow(new UnparseableStatement(
            OkCreditStatementParser.CODE_PARSE_FAILED, "No transactions could be read."));

        assertThatThrownBy(() -> service.parse(authUser, pdf, "stmt.pdf", null))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("PARSE_FAILED"));
    }

    private static LedgerEntry ledgerEntry(EntryType type, String amount, String note, LocalDate date) {
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryType(type);
        entry.setAmount(new BigDecimal(amount));
        entry.setNote(note);
        entry.setEntryAt(date.atStartOfDay(ZONE).toInstant());
        return entry;
    }
}