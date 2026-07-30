package com.vyapaarmitra.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.TrustBucket;
import com.vyapaarmitra.api.dashboard.DashboardService.SummaryResponse;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceRepository;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.subscription.PlanCatalog;
import com.vyapaarmitra.api.subscription.PlanService;
import com.vyapaarmitra.api.subscription.PlanTier;
import com.vyapaarmitra.api.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private AppTime appTime;
    @Mock private PlanService planService;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 30);
    private final UUID businessId = UUID.randomUUID();
    private final AuthUser authUser = new AuthUser(UUID.randomUUID(), businessId, null);

    private DashboardService service() {
        return new DashboardService(customerRepository, supplierRepository, ledgerEntryRepository,
            invoiceRepository, branchAccessService, appTime, planService);
    }

    /** Stub the shared reads every summary() call makes, independent of plan tier. */
    private void stubCommon() {
        Set<UUID> branchIds = Set.of(UUID.randomUUID());
        when(branchAccessService.scope(any(), any())).thenReturn(branchIds);
        when(appTime.today()).thenReturn(TODAY);
        when(appTime.startOfDay(any()))
            .thenAnswer(inv -> ((LocalDate) inv.getArgument(0)).atStartOfDay(ZoneOffset.UTC).toInstant());

        when(customerRepository.topDebtors(any(), any())).thenReturn(List.of());
        when(customerRepository.totalOutstanding(any())).thenReturn(BigDecimal.TEN);
        when(customerRepository.totalOverdue(any(), any())).thenReturn(BigDecimal.ONE);
        when(customerRepository.countOverdue(any(), any())).thenReturn(3L);
        when(ledgerEntryRepository.sumByTypeBetween(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countBetween(any(), any(), any())).thenReturn(0L);

        // Sales & billing
        when(invoiceRepository.sumGrandTotalBetween(any(), eq(startOf(TODAY)), eq(startOf(TODAY.plusDays(1)))))
            .thenReturn(new BigDecimal("1200"));                               // salesToday
        when(invoiceRepository.sumGrandTotalBetween(any(), eq(startOf(TODAY.withDayOfMonth(1))),
            eq(startOf(TODAY.withDayOfMonth(1).plusMonths(1))))).thenReturn(new BigDecimal("45000")); // salesMonth
        when(invoiceRepository.countCreatedBetweenBranch(any(), any(), any())).thenReturn(12L);
        when(invoiceRepository.countByTypeBetweenBranch(any(), eq(BillType.PAKKA), any(), any())).thenReturn(4L);
        when(supplierRepository.totalPayable(any())).thenReturn(new BigDecimal("8000"));

        // Overdue aging — distinct value per tier, keyed on the exclusive upper bound.
        when(customerRepository.countOverdueByDueDateRange(any(), any(), eq(TODAY))).thenReturn(1L);
        when(customerRepository.countOverdueByDueDateRange(any(), any(), eq(TODAY.minusDays(7)))).thenReturn(2L);
        when(customerRepository.countOverdueByDueDateRange(any(), any(), eq(TODAY.minusDays(30)))).thenReturn(3L);
        when(customerRepository.countOverdueOlderThan(any(), eq(TODAY.minusDays(90)))).thenReturn(5L);
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Test
    void populatesBusinessFieldsAndTrustDistributionOnPro() {
        stubCommon();
        when(planService.entitlements(businessId)).thenReturn(PlanCatalog.entitlements(PlanTier.PRO));
        when(customerRepository.countByTrustBucket(any(), eq(TrustBucket.GOOD))).thenReturn(7L);
        when(customerRepository.countByTrustBucket(any(), eq(TrustBucket.WATCH))).thenReturn(2L);
        when(customerRepository.countByTrustBucket(any(), eq(TrustBucket.RISKY))).thenReturn(1L);

        SummaryResponse s = service().summary(authUser, null);

        assertThat(s.salesToday()).isEqualByComparingTo("1200");
        assertThat(s.salesMonth()).isEqualByComparingTo("45000");
        assertThat(s.billsMonth()).isEqualTo(12L);
        assertThat(s.pakkaMonth()).isEqualTo(4L);
        assertThat(s.payableToSuppliers()).isEqualByComparingTo("8000");
        assertThat(s.overdueAging().upto7()).isEqualTo(1L);
        assertThat(s.overdueAging().upto30()).isEqualTo(2L);
        assertThat(s.overdueAging().upto90()).isEqualTo(3L);
        assertThat(s.overdueAging().over90()).isEqualTo(5L);
        assertThat(s.trustDistribution()).isNotNull();
        assertThat(s.trustDistribution().good()).isEqualTo(7L);
        assertThat(s.trustDistribution().watch()).isEqualTo(2L);
        assertThat(s.trustDistribution().risky()).isEqualTo(1L);
        assertThat(s.computedAt()).isNotNull();
    }

    @Test
    void trustDistributionIsNullWhenNotEntitled() {
        stubCommon();
        when(planService.entitlements(businessId)).thenReturn(PlanCatalog.entitlements(PlanTier.LITE));

        SummaryResponse s = service().summary(authUser, null);

        assertThat(s.trustDistribution()).isNull();
        // Business breadth is plan-independent — still present on Lite.
        assertThat(s.salesToday()).isEqualByComparingTo("1200");
        assertThat(s.payableToSuppliers()).isEqualByComparingTo("8000");
    }
}
