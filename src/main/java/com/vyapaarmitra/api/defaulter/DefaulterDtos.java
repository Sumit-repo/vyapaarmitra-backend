package com.vyapaarmitra.api.defaulter;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class DefaulterDtos {

    private DefaulterDtos() {
    }

    /** Merchant flags one of their own customers who is 90+ days overdue. */
    public record WarnRequest(@NotNull UUID customerId) {
    }

    /**
     * The report state after warning. {@code activatesAt} is when the Defaulter badge goes
     * live on the network if still unpaid (warning + 7-day grace).
     */
    public record DefaulterReportView(DefaulterStatus status, Instant warningSentAt, Instant activatesAt) {
    }

    /** Anonymized network signal for an exact phone — flagged, or not. Nothing else is shared. */
    public record RiskView(boolean flagged) {
    }

    /** The caller's defaulter-network consent state (Settings toggle). */
    public record ConsentView(boolean consent) {
    }

    public record ConsentRequest(@NotNull Boolean consent) {
    }
}
