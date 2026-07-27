package com.vyapaarmitra.api.subscription;

/**
 * Thrown when an action is blocked by the caller's plan — a metered cap
 * (entries/day, GST bills/month) or a locked feature. Surfaces as HTTP 402 with
 * {@code {error:{code:"PLAN_LIMIT", reason, message}}}; the web maps {@code reason}
 * straight onto the paywall's UpgradeReason so the modal opens with the right copy.
 */
public class PlanLimitException extends RuntimeException {

    private final String reason;

    public PlanLimitException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
