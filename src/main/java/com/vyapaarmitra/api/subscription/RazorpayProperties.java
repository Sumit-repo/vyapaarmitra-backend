package com.vyapaarmitra.api.subscription;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials + the mapping from our (tier, period) to the Plan entities
 * created once in the Razorpay dashboard. All values come from the environment;
 * blank keys mean billing is disabled (checkout returns a clear error).
 *
 * @param keyId         public key id (rzp_test_* / rzp_live_*), also sent to the client
 * @param keySecret     secret used for API basic-auth (server-only)
 * @param webhookSecret HMAC secret that signs webhook bodies (server-only)
 * @param plans         key "TIER_PERIOD" (e.g. "PRO_YEARLY") → Razorpay plan_id
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret,
                                 Map<String, String> plans) {

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    /** The Razorpay plan_id for a tier+period, or null if not mapped. */
    public String planId(PlanTier tier, BillingPeriod period) {
        if (plans == null) {
            return null;
        }
        return plans.get(tier.name() + "_" + period.name());
    }
}
