package com.vyapaarmitra.api.subscription;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Razorpay's {@code X-Razorpay-Signature} header: HMAC-SHA256 of the raw
 * request body with the webhook secret, hex-encoded. Pure + static so it can be
 * unit-tested against Razorpay's published vectors without any Spring context.
 */
public final class RazorpaySignature {

    private RazorpaySignature() {
    }

    public static boolean verify(byte[] body, String signature, String secret) {
        if (signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(body, secret);
        // Constant-time compare to avoid leaking the signature byte-by-byte.
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }
}
