package com.vyapaarmitra.api.billlayout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Cache contract: same bytes per key, TTL expiry, size cap, uncacheable keys ⇒ null. */
class BillPreviewCacheTest {

    @Test
    void hitReturnsTheSameBytes() {
        BillPreviewCache cache = new BillPreviewCache();
        byte[] pdf = {1, 2, 3};
        cache.put(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, null), pdf);

        assertThat(cache.get(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, null)))
            .isSameAs(pdf);
    }

    @Test
    void missReturnsNull() {
        assertThat(new BillPreviewCache().get("nope")).isNull();
    }

    @Test
    void aZeroTtlExpiresImmediately() {
        BillPreviewCache cache = new BillPreviewCache(Duration.ZERO, 8);
        cache.put("k", new byte[1]);

        assertThat(cache.get("k")).isNull();
    }

    @Test
    void atCapacityTheBackstopEvictsBeforeStoring() {
        BillPreviewCache cache = new BillPreviewCache(Duration.ofMinutes(5), 2);
        cache.put("a", new byte[1]);
        cache.put("b", new byte[1]);
        cache.put("c", new byte[1]);

        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isNotNull();
    }

    @Test
    void anOversizePdfIsNeverStored() {
        BillPreviewCache cache = new BillPreviewCache();
        cache.put("big", new byte[2 * 1024 * 1024 + 1]);

        assertThat(cache.get("big")).isNull();
    }

    @Test
    void keyOfTrimsTheNote() {
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, "  Thanks  "))
            .isEqualTo(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, "Thanks"));
    }

    @Test
    void keyOfNormalisesBlankAndNullNotes() {
        assertThat(BillPreviewCache.keyOf(BillPreset.MINIMAL, true, false, "   "))
            .isEqualTo(BillPreviewCache.keyOf(BillPreset.MINIMAL, true, false, null));
    }

    @Test
    void keyOfReturnsNullForAnOverlongNote() {
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, "x".repeat(501)))
            .isNull();
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, false, false, "x".repeat(500)))
            .isNotNull();
    }
}