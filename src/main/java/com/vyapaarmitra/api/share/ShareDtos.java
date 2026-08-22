package com.vyapaarmitra.api.share;

import com.vyapaarmitra.api.ledger.LedgerDtos.EntryResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ShareDtos {

    private ShareDtos() {
    }

    /** Returned to the shopkeeper when a link is created; the client builds the /s/{token} URL. */
    public record ShareTokenResponse(String token) {
    }

    /** Pre-verification metadata — deliberately no party PII. */
    public record ShareMetaResponse(String shopName, ShareType type, boolean locked) {
    }

    public record VerifyRequest(@NotBlank @Pattern(regexp = "\\d{4}", message = "Enter 4 digits") String last4) {
    }

    /** Short-lived, share-scoped token the viewer sends with data/PDF requests. */
    public record VerifyResponse(String viewToken) {
    }

    /** The customer-facing ledger view (bounded window). */
    public record ShareLedgerResponse(String shopName, String customerName, BigDecimal balance,
                                      LocalDate oldestDueDate, List<EntryResponse> entries) {
    }
}
