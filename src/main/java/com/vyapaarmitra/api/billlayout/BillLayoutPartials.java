package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import com.vyapaarmitra.api.invoice.InvoiceItemJson;
import com.vyapaarmitra.api.pdf.HtmlDoc;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Markup shared by the three bill-design presets, plus the rules that keep a bill legal.
 * Strict XHTML, CSS 2.1, tables only (openhtmltopdf has no flexbox); each preset composes
 * these partials with its own stylesheet.
 *
 * The two legally-distinct bills are enforced here, not per preset:
 *  - KACCHA → titled INVOICE, no GSTIN, no tax columns (printing "Tax Invoice"/GSTIN on a
 *    no-GST bill invites CGST §122 penalties).
 *  - PAKKA  → TAX INVOICE with seller/buyer GSTIN, HSN + rate per item, CGST+SGST or IGST.
 *
 * Labels are English by convention; ₹ and Devanagari render via the bundled NotoSans font
 * (see PdfRenderer).
 */
final class BillLayoutPartials {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    // openhtmltopdf renders the <a href> as a clickable link in the PDF.
    static final String BRAND_TAGLINE =
        "<a href=\"https://vyapaarmitra.vercel.app/\">VyapaarMitra</a>"
        + " - free khata &amp; GST billing for shops";

    private BillLayoutPartials() {
    }

    /** A no-GST bill must never read "Tax Invoice" — the custom label is PAKKA-only. */
    static String heading(BillType type, BillLayoutOptions o) {
        if (type == BillType.PAKKA && notBlank(o.headingLabel())) {
            return esc(o.headingLabel());
        }
        return type == BillType.PAKKA ? "TAX INVOICE" : "INVOICE";
    }

    /** Column head row — pakka adds the HSN and GST columns. */
    static String itemsHead(boolean pakka) {
        return "<thead><tr><th class=\"idx\">#</th><th>Item</th>"
            + (pakka ? "<th class=\"hsn\">HSN</th>" : "")
            + "<th class=\"num\">Qty</th><th class=\"num\">Rate</th>"
            + (pakka ? "<th class=\"num\">GST</th>" : "")
            + "<th class=\"num\">Amount</th></tr></thead>";
    }

    /** Item rows (or a "No items" placeholder spanning the pakka/kaccha column count). */
    static String itemsRows(InvoiceResponse bill) {
        boolean pakka = bill.billType() == BillType.PAKKA;
        StringBuilder rows = new StringBuilder();
        int i = 1;
        for (InvoiceItemJson it : bill.items()) {
            rows.append("<tr>")
                .append(td("idx", String.valueOf(i++)))
                .append(td(null, esc(it.name())))
                .append(pakka ? td("hsn", esc(nullTo(it.hsn(), ""))) : "")
                .append(td("num", plain(it.qty()) + " " + esc(nullTo(it.unit(), ""))))
                .append(td("num", rupees(it.rate())))
                .append(pakka ? td("num", plain(it.taxRate()) + "%") : "")
                .append(td("num", rupees(it.lineAmount())))
                .append("</tr>");
        }
        if (bill.items().isEmpty()) {
            rows.append("<tr><td colspan=\"").append(pakka ? 7 : 5)
                .append("\" class=\"muted\">No items</td></tr>");
        }
        return rows.toString();
    }

    /** Subtotal → discount → CGST+SGST or IGST → grand total → received. */
    static String totalsRows(InvoiceResponse bill) {
        boolean pakka = bill.billType() == BillType.PAKKA;
        StringBuilder totals = new StringBuilder();
        totals.append(totalRow("Subtotal", rupees(bill.subtotal()), false));
        if (pos(bill.discount())) totals.append(totalRow("Discount", "- " + rupees(bill.discount()), false));
        if (pakka && pos(bill.taxTotal())) {
            if (pos(bill.igst())) {
                totals.append(totalRow("IGST", rupees(bill.igst()), false));
            } else {
                totals.append(totalRow("CGST", rupees(bill.cgst()), false));
                totals.append(totalRow("SGST", rupees(bill.sgst()), false));
            }
        }
        totals.append(totalRow("Grand total", rupees(bill.grandTotal()), true));
        if (pos(bill.amountReceived()) && pos(bill.balanceDue())) {
            totals.append(totalRow("Received", rupees(bill.amountReceived()), false));
        }
        return totals.toString();
    }

    static String balanceBlock(InvoiceResponse bill, BillLayoutOptions o) {
        if (o.hideBalance()) {
            return "";
        }
        return pos(bill.balanceDue())
            ? "<div class=\"balance due\">Balance on khata: " + rupees(bill.balanceDue()) + "</div>"
            : "<div class=\"balance paid\">Paid in full</div>";
    }

    /** Seller GSTIN under the shop name — pakka only. */
    static String sellerGstinBlock(InvoiceResponse bill) {
        return bill.billType() == BillType.PAKKA && notBlank(bill.sellerGstin())
            ? "<div>GSTIN: " + esc(bill.sellerGstin()) + "</div>" : "";
    }

    static String supplyBlock(InvoiceResponse bill) {
        return bill.billType() == BillType.PAKKA && notBlank(bill.placeOfSupply())
            ? "<div>Place of supply: " + esc(bill.placeOfSupply()) + "</div>" : "";
    }

    /** "Bill to" block — omitted for walk-in bills with no name. */
    static String buyerBlock(InvoiceResponse bill, BillLayoutOptions o) {
        boolean pakka = bill.billType() == BillType.PAKKA;
        return notBlank(bill.partyName())
            ? "<div class=\"muted\">Bill to</div>"
              + "<div class=\"party-name\">" + esc(bill.partyName()) + "</div>"
              + (!o.hidePhone() && notBlank(bill.partyPhone()) ? "<div>" + esc(bill.partyPhone()) + "</div>" : "")
              + (pakka && notBlank(bill.partyGstin()) ? "<div>GSTIN: " + esc(bill.partyGstin()) + "</div>" : "")
            : "";
    }

    static String notesBlock(InvoiceResponse bill) {
        return notBlank(bill.notes())
            ? "<div class=\"notes\"><span class=\"muted\">Notes:</span> " + esc(bill.notes()) + "</div>" : "";
    }

    /** The merchant's own footer line (bill-design toggle), above the brand tag. */
    static String footerNoteBlock(String footerNote) {
        return notBlank(footerNote) ? "<div class=\"footer-note\">" + esc(footerNote) + "</div>" : "";
    }

    /** FREE-plan "powered by" footer — the caller decides from the effective plan. */
    static String brandBlock(boolean branding, BillLayoutOptions o) {
        return branding && !o.hideBrand() ? "<div class=\"brand\">" + BRAND_TAGLINE + "</div>" : "";
    }

    /** Shop logo, only when the design turns it on and one was uploaded. */
    static String logoImg(BillPdfData data) {
        return data.options().showLogo() && notBlank(data.logoUrl())
            ? "<img class=\"logo\" src=\"" + esc(data.logoUrl()) + "\" alt=\"Shop logo\" />" : "";
    }

    /**
     * UPI QR + "Scan &amp; Pay ₹X" label, right-aligned under the balance. Rendered only
     * when the design asks for it, the shop has a VPA, and money is still owed.
     */
    static String qrBlock(BillPdfData data) {
        if (!data.options().showUpiQr() || !notBlank(data.upiVpa()) || !pos(data.bill().balanceDue())) {
            return "";
        }
        String uri = BillQr.upiDataUri(data.upiVpa(), data.upiPayeeName(), data.bill().balanceDue());
        return "<table class=\"qr\"><tr><td class=\"qr-cell\">"
            + "<img class=\"qr-img\" src=\"" + uri + "\" alt=\"UPI QR\" /></td>"
            + "<td class=\"qr-label\">Scan &amp; Pay " + rupees(data.bill().balanceDue()) + "</td>"
            + "</tr></table>";
    }

    /**
     * Balance line + UPI QR as one full-width pay band when BOTH render — "how much you
     * owe" sits next to the way to clear it, instead of two stacked blocks the customer's
     * eye has to connect. When only one renders, this returns the original blocks byte
     * for byte: that's what keeps the CLASSIC golden green (its fixture has no VPA, so
     * no QR, so no band).
     */
    static String payBlock(String balance, String qr) {
        if (qr.isEmpty() || balance.isEmpty()) {
            return balance + qr;
        }
        return "<table class=\"pay\"><tr><td class=\"pay-due\">" + balance
            + "</td><td class=\"pay-qr\">" + qr + "</td></tr></table>";
    }

    /** Band chrome, emitted only when the band rendered (byte-identical otherwise). */
    static String payBandCss(String balance, String qr) {
        if (qr.isEmpty() || balance.isEmpty()) {
            return "";
        }
        return ".pay { width: 100%; margin-top: 12px; border-collapse: collapse; border: 1px solid #e6e6e6; }"
            + ".pay td { padding: 10px 12px; vertical-align: middle; }"
            + ".pay .balance { margin-top: 0; text-align: left; }"
            + ".pay-qr { text-align: right; width: 1%; }"
            + ".pay .qr { margin-top: 0; }";
    }

    static String date(InvoiceResponse bill, BillLayoutOptions o) {
        if (bill.createdAt() == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(o.dateFormat().pattern(), Locale.ENGLISH)
            .format(bill.createdAt().atZone(IST));
    }

    /**
     * CSS overrides for the v2 design options, appended after a preset's own stylesheet
     * (later rules win). Emits nothing at the defaults, which is what keeps the CLASSIC
     * golden byte-identical — options only ever ADD markup, never reshape it.
     */
    static String designOverrides(BillLayoutOptions o) {
        StringBuilder css = new StringBuilder();
        if (o.headingAlign() != BillLayoutOptions.Align.LEFT) {
            String align = o.headingAlign().name().toLowerCase(Locale.ENGLISH);
            css.append(".head-left, .band-left { text-align: ").append(align).append("; }");
            // A fixed-width <img> ignores text-align — center/right it by margins.
            css.append(o.headingAlign() == BillLayoutOptions.Align.CENTER
                ? ".head .logo, .band .logo { margin-left: auto; margin-right: auto; }"
                : ".head .logo, .band .logo { margin-left: auto; }");
        }
        if (o.footerAlign() != BillLayoutOptions.Align.RIGHT) {
            css.append(".footer-note { text-align: ")
                .append(o.footerAlign().name().toLowerCase(Locale.ENGLISH)).append("; }");
        }
        String accent = accentHex(o.accent());
        if (accent != null) {
            css.append(".head { border-bottom-color: ").append(accent).append("; }")
                .append(".band { background-color: ").append(accent).append("; }")
                .append(".totals .grand td { border-top-color: ").append(accent).append("; }")
                .append(".totals { border-color: ").append(accent).append("; }")
                .append(".pay { border-color: ").append(accent).append("; }");
        }
        return css.toString();
    }

    /** NEUTRAL returns null (no override — the preset stylesheet stands). */
    private static String accentHex(BillLayoutOptions.Accent accent) {
        return switch (accent) {
            case BLUE -> "#015FAD";
            case GREEN -> "#1E7B3C";
            case MAROON -> "#7A1F2B";
            case NEUTRAL -> null;
        };
    }

    private static String td(String cls, String html) {
        return cls == null ? "<td>" + html + "</td>" : "<td class=\"" + cls + "\">" + html + "</td>";
    }

    private static String totalRow(String label, String value, boolean grand) {
        return "<tr class=\"" + (grand ? "grand" : "") + "\"><td>" + label + "</td>"
            + "<td class=\"num\">" + value + "</td></tr>";
    }

    /** Indian-grouped rupees — one source of truth in HtmlDoc. */
    static String rupees(BigDecimal v) {
        return HtmlDoc.rupees(v);
    }

    private static String plain(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    private static boolean pos(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullTo(String s, String fallback) {
        return s == null ? fallback : s;
    }

    /** Escape the five XML-significant chars so shop/customer/item text can't break the doc. */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}