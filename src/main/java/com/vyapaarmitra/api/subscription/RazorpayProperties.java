package com.vyapaarmitra.api.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials, all sourced from the environment. Blank keys mean billing is
 * disabled (checkout returns a clear "payments not set up" error).
 *
 * @param keyId           public key id (rzp_test_* / rzp_live_*), also sent to the client
 * @param keySecret       secret used for API basic-auth (server-only)
 * @param webhookSecret   HMAC secret that signs webhook bodies (server-only)
 * @param callbackBaseUrl base URL the hosted payment page redirects back to after
 *                        payment (the web /billing/thanks screen, which verifies and
 *                        deep-links into the app). Blank → Razorpay shows its own
 *                        end page and the buyer returns by hand.
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret,
                                 String callbackBaseUrl) {

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}
