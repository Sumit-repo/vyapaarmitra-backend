package com.vyapaarmitra.api.ledger;

import com.vyapaarmitra.api.customer.CustomerDtos.CustomerResponse;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record CreateEntryRequest(@NotNull UUID customerId,
                                     @NotNull EntryType entryType,
                                     @NotNull @Positive @Digits(integer = 12, fraction = 2)
                                     BigDecimal amount,
                                     @Size(max = 20) String method,
                                     @Size(max = 500) String note,
                                     LocalDate dueDate) {
    }

    public record EntryResponse(UUID id, UUID customerId, EntryType entryType, BigDecimal amount,
                                String method, String note, LocalDate dueDate, Instant entryAt,
                                BigDecimal balanceAfter) {

        /** {@code balanceAfter} is the running balance after this entry (server-computed). */
        public static EntryResponse from(LedgerEntry e, BigDecimal balanceAfter) {
            return new EntryResponse(e.getId(), e.getCustomerId(), e.getEntryType(), e.getAmount(),
                e.getMethod(), e.getNote(), e.getDueDate(), e.getEntryAt(), balanceAfter);
        }
    }

    /** Returned after creating an entry so the mobile app can update UI in one round trip. */
    public record EntryCreatedResponse(EntryResponse entry, CustomerResponse customer) {
    }
}
