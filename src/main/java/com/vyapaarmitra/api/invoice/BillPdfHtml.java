package com.vyapaarmitra.api.invoice;

import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the XHTML for a bill PDF (rendered by {@link com.vyapaarmitra.api.pdf.PdfRenderer}).
 * Ported from the mobile HTML builder; the two legally-distinct layouts:
 *  - KACCHA → titled ESTIMATE, no GSTIN, no tax columns (printing "Tax Invoice"/GSTIN on a
 *    no-GST bill invites penalties).
 *  - PAKKA  → TAX INVOICE with seller/buyer GSTIN, HSN + rate per item, and CGST+SGST or IGST.
 *
 * Table-based layout (openhtmltopdf has no flexbox). Labels English by convention. Branding
 * footer is plan-gated by the caller.
 */
public final class BillPdfHtml {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    // ₹ and Devanagari render via the bundled NotoSans font (see PdfRenderer).
    private static final String BRAND_TAGLINE =
        "Banaya gaya VyapaarMitra se - free khata &amp; GST billing for shops";

    private BillPdfHtml() {
    }

    public static String build(InvoiceResponse bill, String shopName, boolean branding) {
        boolean pakka = bill.billType() == BillType.PAKKA;
        String heading = pakka ? "TAX INVOICE" : "ESTIMATE";

        StringBuilder rows = new StringBuilder();
        int i = 1;
        for (InvoiceItemJson it : bill.items()) {
            rows.append("<tr>")
                .append(td("idx", String.valueOf(i++)))
                .append(td(null, esc(it.name())))
                .append(pakka ? td("hsn", esc(nullTo(it.hsn(), ""))) : "")
                .append(td("num", plain(it.qty()) + " " + esc(nullTo(it.unit(), ""))))
                .append(td("num", rupees(it.rate())))
                .append(pakka ? td("num", plain(nz(it.taxRate())) + "%") : "")
                .append(td("num", rupees(it.lineAmount())))
                .append("</tr>");
        }
        int colCount = pakka ? 7 : 5;
        if (bill.items().isEmpty()) {
            rows.append("<tr><td colspan=\"").append(colCount).append("\" class=\"muted\">No items</td></tr>");
        }

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

        String balance = pos(bill.balanceDue())
            ? "<div class=\"balance due\">Balance on khata: " + rupees(bill.balanceDue()) + "</div>"
            : "<div class=\"balance paid\">Paid in full</div>";

        String sellerGstin = pakka && notBlank(bill.sellerGstin())
            ? "<div>GSTIN: " + esc(bill.sellerGstin()) + "</div>" : "";
        String supply = pakka && notBlank(bill.placeOfSupply())
            ? "<div>Place of supply: " + esc(bill.placeOfSupply()) + "</div>" : "";

        String buyer = notBlank(bill.partyName())
            ? "<div class=\"muted\">Bill to</div>"
              + "<div class=\"party-name\">" + esc(bill.partyName()) + "</div>"
              + (notBlank(bill.partyPhone()) ? "<div>" + esc(bill.partyPhone()) + "</div>" : "")
              + (pakka && notBlank(bill.partyGstin()) ? "<div>GSTIN: " + esc(bill.partyGstin()) + "</div>" : "")
            : "";

        String notes = notBlank(bill.notes())
            ? "<div class=\"notes\"><span class=\"muted\">Notes:</span> " + esc(bill.notes()) + "</div>" : "";

        String brand = branding ? "<div class=\"brand\">" + BRAND_TAGLINE + "</div>" : "";
        String date = bill.createdAt() == null ? "" : DATE.format(bill.createdAt().atZone(IST));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html><head><meta charset=\"utf-8\" />"
            + "<style>" + css() + "</style></head><body>"
            + "<table class=\"head\"><tr>"
            + "<td class=\"head-left\"><div class=\"shop-name\">" + esc(shopName) + "</div>" + sellerGstin + "</td>"
            + "<td class=\"head-right\"><div class=\"h\">" + heading + "</div>"
            + "<div>" + esc(bill.number()) + "</div><div class=\"muted\">" + esc(date) + "</div></td>"
            + "</tr></table>"
            + "<table class=\"meta\"><tr><td class=\"party\">" + buyer + "</td>"
            + "<td class=\"supply\">" + supply + "</td></tr></table>"
            + "<table class=\"items\"><thead><tr>"
            + "<th class=\"idx\">#</th><th>Item</th>"
            + (pakka ? "<th class=\"hsn\">HSN</th>" : "")
            + "<th class=\"num\">Qty</th><th class=\"num\">Rate</th>"
            + (pakka ? "<th class=\"num\">GST</th>" : "")
            + "<th class=\"num\">Amount</th></tr></thead><tbody>" + rows + "</tbody></table>"
            + "<table class=\"totals\">" + totals + "</table>"
            + balance + notes + brand
            + "</body></html>";
    }

    private static String css() {
        return "* { box-sizing: border-box; }"
            + "body { font-family: 'NotoSans', sans-serif; color: #17181A; font-size: 12px; }"
            + ".head { width: 100%; border-bottom: 2px solid #17181A; }"
            + ".head-right { text-align: right; }"
            + ".shop-name { font-size: 20px; font-weight: bold; }"
            + ".head .h { font-size: 17px; font-weight: bold; }"
            + ".muted { color: #6b6b6b; font-size: 10px; }"
            + ".meta { width: 100%; margin-top: 12px; }"
            + ".meta .supply { text-align: right; vertical-align: top; }"
            + ".party-name { font-weight: bold; font-size: 13px; }"
            + ".items { width: 100%; border-collapse: collapse; margin-top: 14px; }"
            + ".items th { text-align: left; font-size: 10px; color: #6b6b6b; border-bottom: 1px solid #ddd; padding: 5px 6px; }"
            + ".items td { padding: 6px; border-bottom: 1px solid #f0f0f0; }"
            + ".num { text-align: right; }"
            + ".idx { color: #9a9a9a; }"
            + ".totals { width: 260px; margin-left: auto; margin-top: 12px; border-collapse: collapse; }"
            + ".totals td { padding: 3px 0; }"
            + ".totals .grand td { border-top: 2px solid #17181A; font-weight: bold; font-size: 14px; padding-top: 6px; }"
            + ".balance { margin-top: 12px; text-align: right; font-weight: bold; }"
            + ".balance.due { color: #C0392B; }"
            + ".balance.paid { color: #1E8E3E; }"
            + ".notes { margin-top: 14px; color: #444; }"
            + ".brand { margin-top: 26px; padding-top: 10px; border-top: 1px dashed #ccc; text-align: center; color: #6b6b6b; font-size: 10px; }";
    }

    private static String td(String cls, String html) {
        return cls == null ? "<td>" + html + "</td>" : "<td class=\"" + cls + "\">" + html + "</td>";
    }

    private static String totalRow(String label, String value, boolean grand) {
        return "<tr class=\"" + (grand ? "grand" : "") + "\"><td>" + label + "</td>"
            + "<td class=\"num\">" + value + "</td></tr>";
    }

    /** Indian-grouped rupees, e.g. 130945 → "₹1,30,945" (rounded to whole rupees for display). */
    static String rupees(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        boolean neg = v.signum() < 0;
        String digits = v.abs().setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder grouped = new StringBuilder();
        int len = digits.length();
        for (int idx = 0; idx < len; idx++) {
            int fromEnd = len - idx;
            grouped.append(digits.charAt(idx));
            if (fromEnd > 3 && (fromEnd - 3) % 2 == 0 && fromEnd != len) grouped.append(',');
        }
        return (neg ? "-" : "") + "₹" + grouped;
    }

    private static String plain(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
