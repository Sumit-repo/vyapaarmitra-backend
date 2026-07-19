package com.vyapaarmitra.api.dashboard;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.TrustBucket;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BranchAccessService branchAccessService;
    private final AppTime appTime;

    public DashboardService(CustomerRepository customerRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            BranchAccessService branchAccessService,
                            AppTime appTime) {
        this.customerRepository = customerRepository;
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
                                  List<TopDebtor> topDebtors) {
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
            topDebtors);
    }
}
