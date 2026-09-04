package com.vyapaarmitra.api.billlayout;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * The bill-design options, persisted as the {@code bill_layouts.options} jsonb column
 * (same record-into-jsonb pattern as the invoice's {@code InvoiceItemJson} items). An
 * empty object deserialises to the defaults, so a plain preset change never flips a
 * toggle. Every non-default value is Pro-gated — including on CLASSIC.
 *
 * <p>Back-compat invariant: rows saved before designer v2 lack the v2 keys, and Jackson
 * deserialises absent enum keys as {@code null} and absent boolean keys as {@code false}
 * — so "hidden" flags default to off (nothing hidden) and enums fall back to today's
 * rendering via {@link #normalized()}. That is why the toggles are phrased as
 * {@code hide*} (default off) rather than {@code show*} (which would default on only
 * for fresh rows).
 */
public record BillLayoutOptions(
    boolean showUpiQr,
    boolean showLogo,
    @Size(max = 500) String footerNote,
    /** Shop-name header block alignment (default LEFT). */
    Align headingAlign,
    /** Merchant footer-note alignment (default RIGHT — the pre-v2 look). */
    Align footerAlign,
    /** Accent for the bill's rules/fills (default NEUTRAL — the pre-v2 near-black). */
    Accent accent,
    /** Omit the customer's phone from the "Bill to" block. */
    boolean hidePhone,
    /** Omit the received/balance block (the "Paid in full" line too). */
    boolean hideBalance,
    /** Omit the "powered by" footer. Pro-only by nature — the whole designer is. */
    boolean hideBrand,
    /** Overrides the PAKKA "TAX INVOICE" heading when set. KACCHA always prints INVOICE. */
    @Size(max = 40) String headingLabel,
    /** Printed date format (default D_MMM_YYYY — the pre-v2 look). */
    DateFormat dateFormat) {

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    public enum Accent {
        NEUTRAL, BLUE, GREEN, MAROON
    }

    public enum DateFormat {
        D_MMM_YYYY("d MMM yyyy"),
        DD_MM_YYYY("dd-MM-yyyy"),
        DD_MM_YYYY_SLASH("dd/MM/yyyy");

        private final String pattern;

        DateFormat(String pattern) {
            this.pattern = pattern;
        }

        /** The {@link java.time.format.DateTimeFormatter} pattern this choice prints. */
        public String pattern() {
            return pattern;
        }
    }

    /**
     * Explicit creator: absent boolean keys arrive as {@code null} (Jackson maps nothing
     * into a primitive record component, and the web mapper runs
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES}), so the params are boxed and coerced to the
     * "off" default. Old rows / old clients that omit the v2 keys keep meaning "off".
     */
    @JsonCreator
    static BillLayoutOptions fromJson(
        @JsonProperty("showUpiQr") Boolean showUpiQr,
        @JsonProperty("showLogo") Boolean showLogo,
        @JsonProperty("footerNote") String footerNote,
        @JsonProperty("headingAlign") Align headingAlign,
        @JsonProperty("footerAlign") Align footerAlign,
        @JsonProperty("accent") Accent accent,
        @JsonProperty("hidePhone") Boolean hidePhone,
        @JsonProperty("hideBalance") Boolean hideBalance,
        @JsonProperty("hideBrand") Boolean hideBrand,
        @JsonProperty("headingLabel") String headingLabel,
        @JsonProperty("dateFormat") DateFormat dateFormat) {
        return new BillLayoutOptions(
            Boolean.TRUE.equals(showUpiQr),
            Boolean.TRUE.equals(showLogo),
            footerNote,
            headingAlign, footerAlign, accent,
            Boolean.TRUE.equals(hidePhone),
            Boolean.TRUE.equals(hideBalance),
            Boolean.TRUE.equals(hideBrand),
            headingLabel, dateFormat);
    }

    public static BillLayoutOptions defaults() {
        return new BillLayoutOptions(false, false, null, null, null, null, false, false, false, null, null);
    }

    /** The three classic toggles with everything else at its default (previews, warm-up). */
    public static BillLayoutOptions standard(boolean showUpiQr, boolean showLogo, String footerNote) {
        return new BillLayoutOptions(showUpiQr, showLogo, footerNote, null, null, null, false, false, false, null, null);
    }

    /**
     * Absent enum keys (old rows, partial JSON) resolve to the pre-v2 rendering; the
     * note is trimmed the same way the upsert path trims it. Idempotent.
     */
    public BillLayoutOptions normalized() {
        return new BillLayoutOptions(showUpiQr, showLogo,
            footerNote == null || footerNote.isBlank() ? null : footerNote.trim(),
            headingAlign == null ? Align.LEFT : headingAlign,
            footerAlign == null ? Align.RIGHT : footerAlign,
            accent == null ? Accent.NEUTRAL : accent,
            hidePhone, hideBalance, hideBrand,
            headingLabel == null || headingLabel.isBlank() ? null : headingLabel.trim(),
            dateFormat == null ? DateFormat.D_MMM_YYYY : dateFormat);
    }
}