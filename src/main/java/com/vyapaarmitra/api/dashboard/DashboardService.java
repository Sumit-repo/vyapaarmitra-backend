package com.vyapaarmitra.api.dashboard;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.TrustBucket;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BranchAccessService branchAccessService;
    private final AppTime appTime;

    public DashboardService(CustomerRepository customerRepository,
                            SupplierRepository supplierRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            BranchAccessService branchAccessService,
                            AppTime appTime) {
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.branchAccessService = branchAccessService;
        this.appTime = appTime;
    }

    public record TopDebtor(UUID customerId, String name, BigDecimal balance, int trustScore,
                            TrustBucket trustBucket) {
    }

    public record SummaryResponse(BigDecimal todayCredit, BigDecimal todayPayment,
                                  long todayEntries, BigDecimal totalOutstanding,
                                  BigDecimal totalOverdue, long overdueCustomers,
                                  BigDecimal totalPayable,
                                  List<TopDebtor> topDebtors) {
    }

    /** One day of ledger activity for the collections trend chart. */
    public record CollectionPoint(LocalDate date, BigDecimal collected, BigDecimal given) {
    }

    @Transactional(readOnly = true)
    public List<CollectionPoint> collections(AuthUser authUser, UUID branchId, int days) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        LocalDate today = appTime.today();
        int n = Math.min(Math.max(days, 1), 90);
        List<CollectionPoint> points = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Instant from = appTime.startOfDay(day);
            Instant to = appTime.startOfDay(day.plusDays(1));
            BigDecimal collected = ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.PAYMENT, from, to);
            BigDecimal given = ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.CREDIT, from, to);
            points.add(new CollectionPoint(day, collected, given));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(AuthUser authUser, UUID branchId) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        LocalDate today = appTime.today();
        Instant from = appTime.startOfDay(today);
        Instant to = appTime.startOfDay(today.plusDays(1));

        List<TopDebtor> topDebtors = customerRepository.topDebtors(branchIds, PageRequest.of(0, 5))
            .stream()
            .map(c -> new TopDebtor(c.getId(), c.getName(), c.getCurrentBalance(),
                c.getTrustScore(), c.getTrustBucket()))
            .toList();

        return new SummaryResponse(
            ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.CREDIT, from, to),
            ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.PAYMENT, from, to),
            ledgerEntryRepository.countBetween(branchIds, from, to),
            customerRepository.totalOutstanding(branchIds),
            customerRepository.totalOverdue(branchIds, today),
            customerRepository.countOverdue(branchIds, today),
            supplierRepository.totalPayable(branchIds),
            topDebtors);
    }
}
