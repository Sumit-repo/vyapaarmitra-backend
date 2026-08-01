package com.vyapaarmitra.api.supplier;

import com.vyapaarmitra.api.ledger.EntryType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    private static final String PHONE_PATTERN = "^\\+?[0-9][0-9\\s-]{5,14}$";
    private static final String PHONE_MESSAGE = "must be a valid phone number";

    public record SupplierResponse(UUID id, UUID branchId, String name, String phone, String address,
                                   List<String> tags, String notes, BigDecimal currentBalance,
                                   LocalDate oldestDueDate, Instant lastActivityAt, boolean active) {

        public static SupplierResponse from(Supplier s) {
            return new SupplierResponse(s.getId(), s.getBranchId(), s.getName(), s.getPhone(),
                s.getAddress(), s.getTags(), s.getNotes(), s.getCurrentBalance(), s.getOldestDueDate(),
                s.getUpdatedAt(), s.isActive());
        }
    }

    /** Compact list shape — small payloads for slow shop connections. */
    public record SupplierListItem(UUID id, String name, String phone, String address,
                                   Instant lastActivityAt, BigDecimal currentBalance) {

        public static SupplierListItem from(Supplier s) {
            return new SupplierListItem(s.getId(), s.getName(), s.getPhone(), s.getAddress(),
                s.getUpdatedAt(), s.getCurrentBalance());
        }
    }

    public record CreateSupplierRequest(@NotNull UUID branchId,
                                        @NotBlank @Size(max = 120) String name,
                                        @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
                                        String phone,
                                        @Size(max = 300) String address,
                                        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
                                        @Size(max = 1000) String notes) {
    }

    public record UpdateSupplierRequest(@Size(max = 120) String name,
                                        @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
                                        String phone,
                                        @Size(max = 300) String address,
                                        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
                                        @Size(max = 1000) String notes,
                                        Boolean active) {
    }

    public record CreateSupplierEntryRequest(@NotNull UUID supplierId,
                                             @NotNull EntryType entryType,
                                             @NotNull @Positive @Digits(integer = 12, fraction = 2)
                                             BigDecimal amount,
                                             @Size(max = 20) String method,
                                             @Size(max = 500) String note,
                                             LocalDate dueDate) {
    }

    public record SupplierEntryResponse(UUID id, UUID supplierId, EntryType entryType,
                                        BigDecimal amount, String method, String note,
                                        LocalDate dueDate, Instant entryAt, BigDecimal balanceAfter,
                                        UUID createdBy, String createdByName) {

        /**
         * {@code balanceAfter} is the running balance after this entry (server-computed);
         * {@code createdByName} is the resolved name of whoever recorded it (business-local).
         */
        public static SupplierEntryResponse from(SupplierLedgerEntry e, BigDecimal balanceAfter,
                                                 String createdByName) {
            return new SupplierEntryResponse(e.getId(), e.getSupplierId(), e.getEntryType(),
                e.getAmount(), e.getMethod(), e.getNote(), e.getDueDate(), e.getEntryAt(), balanceAfter,
                e.getCreatedBy(), createdByName);
        }
    }

    public record SupplierEntryCreatedResponse(SupplierEntryResponse entry, SupplierResponse supplier) {
    }
}
