package com.vyapaarmitra.api.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerResponse(UUID id, UUID branchId, String name, String phone, String address,
                                   List<String> tags, String notes, int trustScore,
                                   TrustBucket trustBucket, BigDecimal currentBalance,
                                   LocalDate oldestDueDate, Instant lastActivityAt, boolean active) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getBranchId(), c.getName(), c.getPhone(),
                c.getAddress(), c.getTags(), c.getNotes(), c.getTrustScore(), c.getTrustBucket(),
                c.getCurrentBalance(), c.getOldestDueDate(), c.getUpdatedAt(), c.isActive());
        }
    }

    /** Compact shape for directory lists — smaller payloads for slow shop connections. */
    public record CustomerListItem(UUID id, String name, String phone, String address,
                                   Instant lastActivityAt, int trustScore,
                                   TrustBucket trustBucket, BigDecimal currentBalance) {

        public static CustomerListItem from(Customer c) {
            return new CustomerListItem(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                c.getUpdatedAt(), c.getTrustScore(), c.getTrustBucket(), c.getCurrentBalance());
        }
    }

    private static final String PHONE_PATTERN = "^\\+?[0-9][0-9\\s-]{5,14}$";
    private static final String PHONE_MESSAGE = "must be a valid phone number";

    public record CreateCustomerRequest(@NotNull UUID branchId,
                                        @NotBlank @Size(max = 120) String name,
                                        @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
                                        String phone,
                                        @Size(max = 300) String address,
                                        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
                                        @Size(max = 1000) String notes) {
    }

    public record UpdateCustomerRequest(@Size(max = 120) String name,
                                        @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
                                        String phone,
                                        @Size(max = 300) String address,
                                        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
                                        @Size(max = 1000) String notes,
                                        Boolean active) {
    }
}
