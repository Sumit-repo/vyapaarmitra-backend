package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RazorpaySignatureTest {

    private static final String SECRET = "whsec_test_123";
    private final byte[] body = "{\"event\":\"subscription.charged\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void knownHmacVectorMatches() {
        // HMAC-SHA256("hello", "key") — a fixed vector to catch algorithm/encoding drift.
        String hex = RazorpaySignature.hmacSha256Hex("hello".getBytes(StandardCharsets.UTF_8), "key");
        assertThat(hex).isEqualTo("9307b3b915efb5171ff14d8cb55fbcc798c6c0ef1456d66ded1a6aa723a58b7b");
    }

    @Test
    void acceptsAValidSignature() {
        String sig = RazorpaySignature.hmacSha256Hex(body, SECRET);
        assertThat(RazorpaySignature.verify(body, sig, SECRET)).isTrue();
    }

    @Test
    void rejectsATamperedSignature() {
        String sig = RazorpaySignature.hmacSha256Hex(body, SECRET);
        String tampered = "0" + sig.substring(1);
        assertThat(RazorpaySignature.verify(body, tampered, SECRET)).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        String sig = RazorpaySignature.hmacSha256Hex(body, "other_secret");
        assertThat(RazorpaySignature.verify(body, sig, SECRET)).isFalse();
    }

    @Test
    void rejectsMissingSignatureOrSecret() {
        assertThat(RazorpaySignature.verify(body, null, SECRET)).isFalse();
        assertThat(RazorpaySignature.verify(body, "abc", null)).isFalse();
        assertThat(RazorpaySignature.verify(body, "abc", "  ")).isFalse();
    }
}
