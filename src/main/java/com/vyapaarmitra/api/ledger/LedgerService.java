package com.vyapaarmitra.api.ledger;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerDtos.CustomerResponse;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.ledger.LedgerDtos.CreateEntryRequest;
import com.vyapaarmitra.api.ledger.LedgerDtos.EntryCreatedResponse;
import com.vyapaarmitra.api.ledger.LedgerDtos.EntryResponse;
import com.vyapaarmitra.api.trust.TrustScoreService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_CREDIT_TERM_DAYS = 30;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final TrustScoreService trustScoreService;
    private final AppTime appTime;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository,
                         CustomerRepository customerRepository,
                         CustomerService customerService,
                         TrustScoreService trustScoreService,
                         AppTime appTime) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.trustScoreService = trustScoreService;
        this.appTime = appTime;
    }

    @Transactional
    public EntryCreatedResponse createEntry(AuthUser authUser, CreateEntryRequest request) {
        Customer customer = customerService.loadAccessible(authUser, request.customerId());

        LedgerEntry entry = new LedgerEntry();
        entry.setBusinessId(customer.getBusinessId());
        entry.setBranchId(customer.getBranchId());
        entry.setCustomerId(customer.getId());
        entry.setEntryType(request.entryType());
        entry.setAmount(request.amount());
        entry.setMethod(request.method());
        entry.setNote(request.note());
        if (request.entryType() == EntryType.CREDIT) {
            entry.setDueDate(request.dueDate() != null
                ? request.dueDate()
                : appTime.today().plusDays(DEFAULT_CREDIT_TERM_DAYS));
        }
        entry.setEntryAt(Instant.now());
        entry.setCreatedBy(authUser.id());
        ledgerEntryRepository.save(entry);

        recomputeCustomerState(customer);
        customerRepository.save(customer);

        // The new entry is the newest, so its running balance is the customer's fresh balance.
        return new EntryCreatedResponse(
            EntryResponse.from(entry, customer.getCurrentBalance()), CustomerResponse.from(customer));
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> ledger(AuthUser authUser, UUID customerId, int page, int size) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<LedgerEntry> entries = ledgerEntryRepository
            .findByCustomerIdOrderByEntryAtDesc(customerId, pageable);
        List<LedgerEntry> content = entries.getContent();

        List<EntryResponse> items;
        if (content.isEmpty()) {
            items = List.of();
        } else {
            // Anchor the page: balance after its newest entry = current balance − everything newer.
            BigDecimal newerSum = ledgerEntryRepository.signedSumAfter(customerId, content.get(0).getEntryAt());
            BigDecimal balanceAfterTop = customer.getCurrentBalance().subtract(newerSum);
            List<BigDecimal> signed = content.stream().map(LedgerService::signed).toList();
            List<BigDecimal> balances = LedgerMath.runningBalancesDesc(signed, balanceAfterTop);
            items = new ArrayList<>(content.size());
            for (int i = 0; i < content.size(); i++) {
                items.add(EntryResponse.from(content.get(i), balances.get(i)));
            }
        }
        return new PageResponse<>(items, entries.getNumber(), entries.getSize(),
            entries.getTotalElements(), entries.getTotalPages());
    }

    /** Signed contribution to the balance: credits add, payments subtract. */
    private static BigDecimal signed(LedgerEntry e) {
        return e.getEntryType() == EntryType.CREDIT ? e.getAmount() : e.getAmount().negate();
    }

    /**
     * Recomputes the customer's denormalized state (balance, oldest due date,
     * trust score) from the full ledger. Per-customer ledgers are small, so a
     * full replay is simpler and safer than incremental updates.
     */
    private void recomputeCustomerState(Customer customer) {
        List<LedgerEntry> entries = ledgerEntryRepository
            .findByCustomerIdOrderByEntryAtAsc(customer.getId());

        List<LedgerMath.CreditLine> credits = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .map(e -> new LedgerMath.CreditLine(e.getAmount(), e.getDueDate()))
            .toList();
        BigDecimal totalPayments = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.PAYMENT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = credits.stream()
            .map(LedgerMath.CreditLine::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = appTime.today();
        LedgerMath.LedgerState state = LedgerMath.compute(credits, totalPayments, today);
        TrustScoreService.TrustResult trust = trustScoreService.compute(
            totalCredits, totalPayments, state.overdueDays(), state.openOverdueCredits());

        customer.setCurrentBalance(state.balance());
        customer.setOldestDueDate(state.balance().compareTo(BigDecimal.ZERO) > 0
            ? state.oldestOpenDueDate()
            : null);
        customer.setTrustScore(trust.score());
        customer.setTrustBucket(trust.bucket());
    }
}
