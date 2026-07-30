package com.vyapaarmitra.api.dashboard;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.TrustBucket;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceRepository;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.subscription.PlanService;
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
    private final InvoiceRepository invoiceRepository;
    private final BranchAccessService branchAccessService;
    private final AppTime appTime;
    private final PlanService planService;

    public DashboardService(CustomerRepository customerRepository,
                            SupplierRepository supplierRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            InvoiceRepository invoiceRepository,
                            BranchAccessService branchAccessService,
                            AppTime appTime,
                            PlanService planService) {
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.invoiceRepository = invoiceRepository;
        this.branchAccessService = branchAccessService;
        this.appTime = appTime;
        this.planService = planService;
    }

    public record TopDebtor(UUID customerId, String name, BigDecimal balance, int trustScore,
                            TrustBucket trustBucket) {
    }

    /** Customer counts per overdue-age tier (days past the oldest due date, business tz). */
    public record OverdueAging(long upto7, long upto30, long upto90, long over90) {
    }

    /** Customer counts per trust bucket. Null on the wire when the plan lacks trust analytics. */
    public record TrustDistribution(long good, long watch, long risky) {
    }

    public record SummaryResponse(BigDecimal todayCredit, BigDecimal todayPayment,
                                  long todayEntries, BigDecimal totalOutstanding,
                                  BigDecimal totalOverdue, long overdueCustomers,
                                  BigDecimal totalPayable,
                                  List<TopDebtor> topDebtors,
                                  // Business breadth beyond the khata — IST-computed, whole-ledger.
                                  BigDecimal salesToday, BigDecimal salesMonth,
                                  long billsMonth, long pakkaMonth,
                                  BigDecimal payableToSuppliers,
                                  OverdueAging overdueAging,
                                  TrustDistribution trustDistribution,
                                  Instant computedAt) {
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

        // Trust scoring is a PRO feature — strip score/bucket from the debtor list otherwise.
        boolean includeTrust = planService.entitlements(authUser.businessId()).trustAnalytics();
        List<TopDebtor> topDebtors = customerRepository.topDebtors(branchIds, PageRequest.of(0, 5))
            .stream()
            .map(c -> new TopDebtor(c.getId(), c.getName(), c.getCurrentBalance(),
                includeTrust ? c.getTrustScore() : 0,
                includeTrust ? c.getTrustBucket() : null))
            .toList();

        // Sales & billing over the IST calendar month (half-open [monthStart, monthEnd)).
        LocalDate monthFirst = today.withDayOfMonth(1);
        Instant monthStart = appTime.startOfDay(monthFirst);
        Instant monthEnd = appTime.startOfDay(monthFirst.plusMonths(1));
        BigDecimal salesToday = invoiceRepository.sumGrandTotalBetween(branchIds, from, to);
        BigDecimal salesMonth = invoiceRepository.sumGrandTotalBetween(branchIds, monthStart, monthEnd);
        long billsMonth = invoiceRepository.countCreatedBetweenBranch(branchIds, monthStart, monthEnd);
        long pakkaMonth = invoiceRepository.countByTypeBetweenBranch(branchIds, BillType.PAKKA, monthStart, monthEnd);

        BigDecimal payableToSuppliers = supplierRepository.totalPayable(branchIds);

        // Overdue aging: bucket by the oldest due date against today's IST date bounds.
        OverdueAging overdueAging = new OverdueAging(
            customerRepository.countOverdueByDueDateRange(branchIds, today.minusDays(7), today),
            customerRepository.countOverdueByDueDateRange(branchIds, today.minusDays(30), today.minusDays(7)),
            customerRepository.countOverdueByDueDateRange(branchIds, today.minusDays(90), today.minusDays(30)),
            customerRepository.countOverdueOlderThan(branchIds, today.minusDays(90)));

        // Trust distribution — null (not zeros) when the plan isn't entitled, so the
        // client keeps showing the upgrade card rather than an empty chart.
        TrustDistribution trustDistribution = includeTrust
            ? new TrustDistribution(
                customerRepository.countByTrustBucket(branchIds, TrustBucket.GOOD),
                customerRepository.countByTrustBucket(branchIds, TrustBucket.WATCH),
                customerRepository.countByTrustBucket(branchIds, TrustBucket.RISKY))
            : null;

        return new SummaryResponse(
            ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.CREDIT, from, to),
            ledgerEntryRepository.sumByTypeBetween(branchIds, EntryType.PAYMENT, from, to),
            ledgerEntryRepository.countBetween(branchIds, from, to),
            customerRepository.totalOutstanding(branchIds),
            customerRepository.totalOverdue(branchIds, today),
            customerRepository.countOverdue(branchIds, today),
            payableToSuppliers,
            topDebtors,
            salesToday, salesMonth, billsMonth, pakkaMonth,
            payableToSuppliers,
            overdueAging, trustDistribution, Instant.now());
    }
}
