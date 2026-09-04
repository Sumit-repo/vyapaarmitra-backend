package com.vyapaarmitra.api.invoice;

import com.vyapaarmitra.api.billlayout.BillPdfData;
import com.vyapaarmitra.api.billlayout.BillPreset;
import com.vyapaarmitra.api.billlayout.BoldBillLayout;
import com.vyapaarmitra.api.billlayout.ClassicBillLayout;
import com.vyapaarmitra.api.billlayout.MinimalBillLayout;

/**
 * Entry point for the bill PDF markup: picks the shop's chosen bill-design preset and
 * hands it the view-model ({@link BillPdfData}). Rendered by
 * {@link com.vyapaarmitra.api.pdf.PdfRenderer}.
 *
 * The presets live in {@code billlayout} next to the design entity; every one of them
 * builds strict XHTML with tables only (openhtmltopdf has no flexbox) and shares the
 * legally-significant parts (KACCHA vs PAKKA, GSTIN, tax columns) via
 * {@code BillLayoutPartials}. CLASSIC is pinned byte-for-byte by {@code BillPdfGoldenTest}
 * — existing bills must not change.
 */
public final class BillPdfHtml {

    private BillPdfHtml() {
    }

    public static String build(BillPdfData data) {
        return switch (data.layout()) {
            case CLASSIC -> ClassicBillLayout.build(data);
            case MINIMAL -> MinimalBillLayout.build(data);
            case BOLD -> BoldBillLayout.build(data);
        };
    }
}