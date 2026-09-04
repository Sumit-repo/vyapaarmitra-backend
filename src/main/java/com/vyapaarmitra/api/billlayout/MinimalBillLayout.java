package com.vyapaarmitra.api.billlayout;

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

import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;

/**
 * Pro preset #1: the quiet version of the classic bill. Same content and column set (the
 * legal parts are shared, see {@link BillLayoutPartials}), but hairline rules instead of
 * heavy borders, a smaller shop name, and a quiet uppercase-style heading. The same class
 * names as CLASSIC are restyled by this preset's own stylesheet.
 */
public final class MinimalBillLayout {

    private MinimalBillLayout() {
    }

    public static String build(BillPdfData d) {
        InvoiceResponse bill = d.bill();
        boolean pakka = bill.billType() == BillType.PAKKA;

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html><head><meta charset=\"utf-8\" />"
            + "<style>" + css() + designOverrides(d.options()) + "</style></head><body>"
            + "<table class=\"head\"><tr>"
            + "<td class=\"head-left\">" + logoImg(d)
            + "<div class=\"shop-name\">" + esc(d.shopName()) + "</div>"
            + sellerGstinBlock(bill) + "</td>"
            + "<td class=\"head-right\"><div class=\"h\">" + heading(bill.billType(), d.options()) + "</div>"
            + "<div class=\"muted\">" + esc(bill.number()) + " | " + esc(date(bill, d.options())) + "</div></td>"
            + "</tr></table>"
            + "<table class=\"meta\"><tr><td class=\"party\">" + buyerBlock(bill, d.options()) + "</td>"
            + "<td class=\"supply\">" + supplyBlock(bill) + "</td></tr></table>"
            + "<table class=\"items\">" + itemsHead(pakka) + "<tbody>" + itemsRows(bill) + "</tbody></table>"
            + "<table class=\"totals\">" + totalsRows(bill) + "</table>"
            + balanceBlock(bill, d.options()) + qrBlock(d) + notesBlock(bill)
            + footerNoteBlock(d.options().footerNote()) + brandBlock(d.branding(), d.options())
            + "</body></html>";
    }

    private static String css() {
        return "* { box-sizing: border-box; }"
            + "body { font-family: 'NotoSans', sans-serif; color: #17181A; font-size: 12px; }"
            + ".head { width: 100%; border-bottom: 1px solid #DDDDDD; }"
            + ".head-right { text-align: right; }"
            + ".shop-name { font-size: 16px; font-weight: bold; letter-spacing: 1px; }"
            + ".head .h { font-size: 11px; color: #6b6b6b; letter-spacing: 3px; }"
            + ".muted { color: #8a8a8a; font-size: 10px; }"
            + ".meta { width: 100%; margin-top: 14px; }"
            + ".meta .supply { text-align: right; vertical-align: top; }"
            + ".party-name { font-weight: bold; font-size: 13px; }"
            + ".items { width: 100%; border-collapse: collapse; margin-top: 16px; }"
            + ".items th { text-align: left; font-size: 9px; color: #9a9a9a; border-bottom: 1px solid #DDDDDD; padding: 4px 6px; }"
            + ".items td { padding: 7px 6px; border-bottom: 1px solid #F5F5F5; }"
            + ".num { text-align: right; }"
            + ".idx { color: #c4c4c4; }"
            + ".totals { width: 240px; margin-left: auto; margin-top: 14px; border-collapse: collapse; }"
            + ".totals td { padding: 4px 0; }"
            + ".totals .grand td { border-top: 1px solid #17181A; font-weight: bold; font-size: 14px; padding-top: 8px; }"
            + ".balance { margin-top: 14px; text-align: right; font-weight: bold; padding: 8px 10px; }"
            + ".balance.due { color: #C0392B; background-color: #FDF1EF; }"
            + ".balance.paid { color: #1E8E3E; background-color: #EEF7F0; }"
            + ".notes { margin-top: 16px; color: #555555; }"
            + ".footer-note { margin-top: 16px; text-align: right; color: #555555; font-size: 11px; }"
            + ".brand { margin-top: 26px; padding-top: 10px; border-top: 1px solid #EEEEEE; text-align: center; color: #8a8a8a; font-size: 10px; }"
            + ".brand a { color: #015FAD; text-decoration: underline; }"
            + ".logo { width: 60px; height: 60px; margin-bottom: 10px; }"
            + ".qr { margin-left: auto; margin-top: 14px; border-collapse: collapse; }"
            + ".qr-img { width: 92px; height: 92px; }"
            + ".qr-label { padding-left: 10px; vertical-align: middle; font-weight: bold; font-size: 11px; }";
    }
}