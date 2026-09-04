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
        cache.put(BillPreviewCache.keyOf(BillPreset.CLASSIC, BillLayoutOptions.defaults()), pdf);

        assertThat(cache.get(BillPreviewCache.keyOf(BillPreset.CLASSIC, BillLayoutOptions.defaults())))
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
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC,
            BillLayoutOptions.standard(false, false, "  Thanks  ")))
            .isEqualTo(BillPreviewCache.keyOf(BillPreset.CLASSIC,
                BillLayoutOptions.standard(false, false, "Thanks")));
    }

    @Test
    void keyOfNormalisesBlankAndNullNotes() {
        assertThat(BillPreviewCache.keyOf(BillPreset.MINIMAL,
            BillLayoutOptions.standard(true, false, "   ")))
            .isEqualTo(BillPreviewCache.keyOf(BillPreset.MINIMAL,
                BillLayoutOptions.standard(true, false, null)));
    }

    @Test
    void keyOfReturnsNullForAnOverlongNote() {
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC,
            BillLayoutOptions.standard(false, false, "x".repeat(501))))
            .isNull();
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC,
            BillLayoutOptions.standard(false, false, "x".repeat(500))))
            .isNotNull();
    }

    @Test
    void keyOfNormalisesAbsentEnumsToTheirDefaults() {
        // Absent enum keys and their explicit pre-v2 defaults are the same design.
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, new BillLayoutOptions(
            false, false, null, null, null, null, false, false, false, null, null)))
            .isEqualTo(BillPreviewCache.keyOf(BillPreset.CLASSIC, new BillLayoutOptions(
                false, false, null, BillLayoutOptions.Align.LEFT, BillLayoutOptions.Align.RIGHT,
                BillLayoutOptions.Accent.NEUTRAL, false, false, false, null,
                BillLayoutOptions.DateFormat.D_MMM_YYYY)));
    }

    @Test
    void keyOfSeparatesEveryOptionField() {
        // Each field must appear in the key — two designs differing in one field
        // must never share bytes.
        BillLayoutOptions base = new BillLayoutOptions(true, true, "note",
            BillLayoutOptions.Align.CENTER, BillLayoutOptions.Align.LEFT,
            BillLayoutOptions.Accent.BLUE, true, true, true, "Label",
            BillLayoutOptions.DateFormat.DD_MM_YYYY);
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, base))
            .isNotEqualTo(BillPreviewCache.keyOf(BillPreset.MINIMAL, base));
        assertThat(BillPreviewCache.keyOf(BillPreset.CLASSIC, base))
            .isNotEqualTo(BillPreviewCache.keyOf(BillPreset.CLASSIC, flip(base)));
    }

    /** A copy of {@code base} with every field different — at least one flip must land. */
    private static BillLayoutOptions flip(BillLayoutOptions base) {
        return new BillLayoutOptions(!base.showUpiQr(), !base.showLogo(),
            base.footerNote() + "!",
            base.headingAlign() == BillLayoutOptions.Align.CENTER
                ? BillLayoutOptions.Align.RIGHT : BillLayoutOptions.Align.CENTER,
            base.footerAlign() == BillLayoutOptions.Align.LEFT
                ? BillLayoutOptions.Align.CENTER : BillLayoutOptions.Align.LEFT,
            base.accent() == BillLayoutOptions.Accent.BLUE
                ? BillLayoutOptions.Accent.GREEN : BillLayoutOptions.Accent.BLUE,
            !base.hidePhone(), !base.hideBalance(), !base.hideBrand(),
            base.headingLabel() + "!",
            base.dateFormat() == BillLayoutOptions.DateFormat.DD_MM_YYYY
                ? BillLayoutOptions.DateFormat.DD_MM_YYYY_SLASH
                : BillLayoutOptions.DateFormat.DD_MM_YYYY);
    }
}