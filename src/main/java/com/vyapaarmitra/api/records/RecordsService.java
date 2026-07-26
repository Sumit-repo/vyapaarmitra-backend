package com.vyapaarmitra.api.records;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntry;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shop-wide "Statement" (passbook) across the scoped branches: a chronological
 * ledger feed with a running net balance (Σ credit − Σ payment) and period totals.
 */
@Service
public class RecordsService {

    private static final int MAX_MONTHS = 6;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final CustomerRepository customerRepository;
    private final BranchAccessService branchAccessService;
    private final AppTime appTime;

    public RecordsService(LedgerEntryRepository ledgerEntryRepository,
                          CustomerRepository customerRepository,
                          BranchAccessService branchAccessService,
                          AppTime appTime) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.customerRepository = customerRepository;
        this.branchAccessService = branchAccessService;
        this.appTime = appTime;
    }

    public record StatementRow(UUID id, UUID customerId, String customerName, EntryType type,
                               BigDecimal amount, String note, Instant entryAt,
                               BigDecimal balanceAfter) {
    }

    public record StatementResponse(List<StatementRow> rows, BigDecimal totalCredit,
                                    BigDecimal totalPayment, BigDecimal closing, int months) {
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(AuthUser authUser, UUID branchId, int months) {
        int span = Math.min(Math.max(1, months), MAX_MONTHS);
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        if (branchIds.isEmpty()) {
            return new StatementResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, span);
        }

        Instant from = appTime.startOfDay(appTime.today().minusMonths(span));
        List<LedgerEntry> entries = ledgerEntryRepository
            .findByBranchIdInAndEntryAtGreaterThanEqualOrderByEntryAtAsc(branchIds, from);

        Map<UUID, String> names = customerNames(entries);

        BigDecimal running = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        List<StatementRow> rows = new ArrayList<>(entries.size());
        // Oldest → newest so the running balance is chronologically correct.
        for (LedgerEntry e : entries) {
            boolean credit = e.getEntryType() == EntryType.CREDIT;
            if (credit) {
                totalCredit = totalCredit.add(e.getAmount());
                running = running.add(e.getAmount());
            } else {
                totalPayment = totalPayment.add(e.getAmount());
                running = running.subtract(e.getAmount());
            }
            rows.add(new StatementRow(e.getId(), e.getCustomerId(),
                names.getOrDefault(e.getCustomerId(), ""), e.getEntryType(), e.getAmount(),
                e.getNote(), e.getEntryAt(), running));
        }
        // Present newest first (passbook order); each row keeps its cumulative balance.
        Collections.reverse(rows);

        return new StatementResponse(rows, totalCredit, totalPayment,
            totalCredit.subtract(totalPayment), span);
    }

    private Map<UUID, String> customerNames(List<LedgerEntry> entries) {
        Set<UUID> ids = entries.stream().map(LedgerEntry::getCustomerId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        customerRepository.findAllById(ids).forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }
}
