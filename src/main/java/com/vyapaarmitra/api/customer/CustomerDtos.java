package com.vyapaarmitra.api.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerResponse(UUID id, UUID branchId, String name, String phone,
                                   List<String> tags, String notes, int trustScore,
                                   TrustBucket trustBucket, BigDecimal currentBalance,
                                   LocalDate oldestDueDate, boolean active) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getBranchId(), c.getName(), c.getPhone(),
                c.getTags(), c.getNotes(), c.getTrustScore(), c.getTrustBucket(),
                c.getCurrentBalance(), c.getOldestDueDate(), c.isActive());
        }
    }

    /** Compact shape for mobile lists — smaller payloads for slow shop connections. */
    public record CustomerListItem(UUID id, String name, String phone, int trustScore,
                                   TrustBucket trustBucket, BigDecimal currentBalance) {

        public static CustomerListItem from(Customer c) {
            return new CustomerListItem(c.getId(), c.getName(), c.getPhone(), c.getTrustScore(),
                c.getTrustBucket(), c.getCurrentBalance());
        }
    }

    public record CreateCustomerRequest(@NotNull UUID branchId, @NotBlank String name,
                                        String phone, List<String> tags, String notes) {
    }

    public record UpdateCustomerRequest(String name, String phone, List<String> tags,
                                        String notes, Boolean active) {
    }
}
