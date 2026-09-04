package com.vyapaarmitra.api.billlayout;

import jakarta.validation.constraints.Size;

/**
 * The bill-design toggles, persisted as the {@code bill_layouts.options} jsonb column
 * (same record-into-jsonb pattern as the invoice's {@code InvoiceItemJson} items). An
 * empty object deserialises to the defaults, so a plain preset change never flips a
 * toggle. Every toggle is Pro-gated — including on CLASSIC.
 */
public record BillLayoutOptions(
    boolean showUpiQr,
    boolean showLogo,
    @Size(max = 500) String footerNote) {

    public static BillLayoutOptions defaults() {
        return new BillLayoutOptions(false, false, null);
    }
}