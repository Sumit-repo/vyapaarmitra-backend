package com.vyapaarmitra.api.recovery;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.TrustBucket;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final BranchAccessService branchAccessService;
    private final AppTime appTime;

    public RecoveryService(CustomerRepository customerRepository,
                           BranchAccessService branchAccessService,
                           AppTime appTime) {
        this.customerRepository = customerRepository;
        this.branchAccessService = branchAccessService;
        this.appTime = appTime;
    }

    public record RecoveryItem(UUID customerId, UUID branchId, String name, String phone,
                               BigDecimal amountDue, LocalDate oldestDueDate, long overdueDays,
                               int trustScore, TrustBucket trustBucket) {
    }

    @Transactional(readOnly = true)
    public PageResponse<RecoveryItem> today(AuthUser authUser, UUID branchId, int page, int size) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        LocalDate today = appTime.today();
        Pageable pageable = PageRequest.of(Math.max(0, page),
            Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        return PageResponse.of(customerRepository.findOverdue(branchIds, today, pageable)
            .map(customer -> toItem(customer, today)));
    }

    private RecoveryItem toItem(Customer customer, LocalDate today) {
        long overdueDays = customer.getOldestDueDate() == null ? 0
            : Math.max(0, ChronoUnit.DAYS.between(customer.getOldestDueDate(), today));
        return new RecoveryItem(customer.getId(), customer.getBranchId(), customer.getName(),
            customer.getPhone(), customer.getCurrentBalance(), customer.getOldestDueDate(),
            overdueDays, customer.getTrustScore(), customer.getTrustBucket());
    }
}
