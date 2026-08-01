package com.vyapaarmitra.api.defaulter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaulterMaintenanceTest {

    @Mock private DefaulterReportRepository reportRepository;
    @Mock private CustomerRepository customerRepository;

    private DefaulterReport lapsedWarning(UUID customerId) {
        DefaulterReport r = new DefaulterReport();
        r.setCustomerId(customerId);
        r.setStatus(DefaulterStatus.WARNING);
        r.setWarningSentAt(Instant.now().minus(8, ChronoUnit.DAYS));
        return r;
    }

    private Customer customerWithBalance(UUID id, BigDecimal balance) {
        Customer c = new Customer();
        c.setId(id);
        c.setCurrentBalance(balance);
        return c;
    }

    @Test
    void activatesAStillUnpaidLapsedWarning() {
        UUID customerId = UUID.randomUUID();
        DefaulterReport r = lapsedWarning(customerId);
        when(reportRepository.findByStatusAndWarningSentAtLessThanEqual(eq(DefaulterStatus.WARNING), any()))
            .thenReturn(List.of(r));
        when(customerRepository.findById(customerId))
            .thenReturn(Optional.of(customerWithBalance(customerId, new BigDecimal("500"))));

        new DefaulterMaintenance(reportRepository, customerRepository).activateLapsedWarnings();

        assertThat(r.getStatus()).isEqualTo(DefaulterStatus.ACTIVE);
        assertThat(r.getActivatedAt()).isNotNull();
    }

    @Test
    void clearsALapsedWarningThatWasAlreadyPaid() {
        UUID customerId = UUID.randomUUID();
        DefaulterReport r = lapsedWarning(customerId);
        when(reportRepository.findByStatusAndWarningSentAtLessThanEqual(eq(DefaulterStatus.WARNING), any()))
            .thenReturn(List.of(r));
        when(customerRepository.findById(customerId))
            .thenReturn(Optional.of(customerWithBalance(customerId, BigDecimal.ZERO)));

        new DefaulterMaintenance(reportRepository, customerRepository).activateLapsedWarnings();

        assertThat(r.getStatus()).isEqualTo(DefaulterStatus.CLEARED);
        assertThat(r.getClearedAt()).isNotNull();
    }
}
