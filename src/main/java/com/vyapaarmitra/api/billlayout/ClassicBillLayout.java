package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;

import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.balanceBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.brandBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.buyerBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.date;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.designOverrides;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.esc;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.footerNoteBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.heading;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.itemsHead;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.itemsRows;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.logoImg;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.notesBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.qrBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.sellerGstinBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.supplyBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.totalsRows;

/**
 * The original bill design — the free default every existing shop already prints. Its
 * markup is pinned byte-for-byte by {@code BillPdfGoldenTest}, so structural edits here
 * change every historical bill PDF: treat this class as frozen. The logo / UPI QR / footer
 * note are the only additions, and each renders as an empty string when off.
 */
public final class ClassicBillLayout {

    private ClassicBillLayout() {
    }

    public static String build(BillPdfData d) {
        InvoiceResponse bill = d.bill();
        boolean pakka = bill.billType() == BillType.PAKKA;
        String heading = heading(bill.billType(), d.options());

        String rows = itemsRows(bill);
        String totals = totalsRows(bill);
        String balance = balanceBlock(bill, d.options());
        String sellerGstin = sellerGstinBlock(bill);
        String supply = supplyBlock(bill);
        String buyer = buyerBlock(bill, d.options());
        String notes = notesBlock(bill);
        String brand = brandBlock(d.branding(), d.options());
        String logo = logoImg(d);
        String qr = qrBlock(d);
        String footnote = footerNoteBlock(d.options().footerNote());
        String date = date(bill, d.options());

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html><head><meta charset=\"utf-8\" />"
            + "<style>" + css() + designCss(logo, qr, footnote) + designOverrides(d.options()) + "</style></head><body>"
            + "<table class=\"head\"><tr>"
            + "<td class=\"head-left\">" + logo + "<div class=\"shop-name\">" + esc(d.shopName()) + "</div>" + sellerGstin + "</td>"
            + "<td class=\"head-right\"><div class=\"h\">" + heading + "</div>"
            + "<div>" + esc(bill.number()) + "</div><div class=\"muted\">" + esc(date) + "</div></td>"
            + "</tr></table>"
            + "<table class=\"meta\"><tr><td class=\"party\">" + buyer + "</td>"
            + "<td class=\"supply\">" + supply + "</td></tr></table>"
            + "<table class=\"items\">" + itemsHead(pakka) + "<tbody>" + rows + "</tbody></table>"
            + "<table class=\"totals\">" + totals + "</table>"
            + balance + qr + notes + footnote + brand
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
            + ".brand { margin-top: 26px; padding-top: 10px; border-top: 1px dashed #ccc; text-align: center; color: #6b6b6b; font-size: 10px; }"
            + ".brand a { color: #015FAD; text-decoration: underline; }";
    }

    /**
     * Bill-design additions, appended only for the toggles that actually rendered so a
     * plain CLASSIC bill keeps its exact pre-bill-design markup (BillPdfGoldenTest).
     */
    private static String designCss(String logo, String qr, String footnote) {
        StringBuilder css = new StringBuilder();
        if (!logo.isEmpty()) {
            // Fixed square: openhtmltopdf has no object-fit, and height:auto is unreliable.
            css.append(".logo { width: 76px; height: 76px; margin-bottom: 8px; }");
        }
        if (!qr.isEmpty()) {
            css.append(".qr { margin-left: auto; margin-top: 12px; border-collapse: collapse; }")
                .append(".qr-img { width: 96px; height: 96px; }")
                .append(".qr-label { padding-left: 10px; vertical-align: middle; font-weight: bold; font-size: 12px; }");
        }
        if (!footnote.isEmpty()) {
            css.append(".footer-note { margin-top: 14px; text-align: right; color: #444; font-size: 11px; }");
        }
        return css.toString();
    }
}