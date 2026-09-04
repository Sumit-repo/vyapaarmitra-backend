package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;

/**
 * Everything the bill PDF builders need, flattened out of the invoice + the shop snapshot
 * so the presets stay pure string-builders with no data access. {@code branding} is the
 * FREE-plan "powered by" footer, decided by the caller from the effective plan.
 */
public record BillPdfData(
    InvoiceResponse bill,
    String shopName,
    String logoUrl,
    String upiVpa,
    String upiPayeeName,
    boolean branding,
    BillPreset layout,
    boolean showUpiQr,
    boolean showLogo,
    String footerNote) {

    /**
     * Snapshot for rendering a stored bill. {@code options} comes from the shop's saved
     * bill design (defaults when they never opened the designer).
     */
    public static BillPdfData of(InvoiceResponse bill, String shopName, String logoUrl,
                                 String upiVpa, String upiPayeeName, BillPreset layout,
                                 BillLayoutOptions options, boolean branding) {
        return new BillPdfData(bill, shopName, logoUrl, upiVpa, upiPayeeName, branding,
            layout, options.showUpiQr(), options.showLogo(), options.footerNote());
    }
}