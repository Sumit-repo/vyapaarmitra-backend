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
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.payBandCss;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.payBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.qrBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.sellerGstinBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.supplyBlock;
import static com.vyapaarmitra.api.billlayout.BillLayoutPartials.totalsRows;

import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;

/**
 * Pro preset #2: the loud one. A full-width ink band carries the shop, the items table
 * gets a dark head row, the totals are boxed and the balance becomes a coloured bar.
 * Content is identical to the other presets — the legal bits live in
 * {@link BillLayoutPartials}.
 */
public final class BoldBillLayout {

    private BoldBillLayout() {
    }

    public static String build(BillPdfData d) {
        InvoiceResponse bill = d.bill();
        boolean pakka = bill.billType() == BillType.PAKKA;
        String balance = balanceBlock(bill, d.options());
        String qr = qrBlock(d);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html><head><meta charset=\"utf-8\" />"
            + "<style>" + css() + payBandCss(balance, qr) + designOverrides(d.options()) + "</style></head><body>"
            + "<table class=\"band\"><tr>"
            + "<td class=\"band-left\">" + logoImg(d)
            + "<div class=\"shop-name\">" + esc(d.shopName()) + "</div>"
            + sellerGstinBlock(bill) + "</td>"
            + "<td class=\"band-right\"><div class=\"h\">" + heading(bill.billType(), d.options()) + "</div>"
            + "<div class=\"muted\">" + esc(bill.number()) + " | " + esc(date(bill, d.options())) + "</div></td>"
            + "</tr></table>"
            + "<table class=\"meta\"><tr><td class=\"party\">" + buyerBlock(bill, d.options()) + "</td>"
            + "<td class=\"supply\">" + supplyBlock(bill) + "</td></tr></table>"
            + "<table class=\"items\">" + itemsHead(pakka) + "<tbody>" + itemsRows(bill) + "</tbody></table>"
            + "<table class=\"totals\">" + totalsRows(bill) + "</table>"
            + payBlock(balance, qr) + notesBlock(bill)
            + footerNoteBlock(d.options().footerNote()) + brandBlock(d.branding(), d.options())
            + "</body></html>";
    }

    private static String css() {
        return "* { box-sizing: border-box; }"
            + "body { font-family: 'NotoSans', sans-serif; color: #17181A; font-size: 12px; }"
            // The band sets its own text colour, so bare child divs (seller GSTIN) inherit white.
            + ".band { width: 100%; background-color: #17181A; border-collapse: collapse; }"
            + ".band td { color: #FFFFFF; padding: 14px 16px; vertical-align: top; }"
            + ".band-left { width: 60%; }"
            + ".band-right { text-align: right; }"
            + ".shop-name { font-size: 21px; font-weight: bold; }"
            + ".band .h { font-size: 13px; font-weight: bold; letter-spacing: 3px; }"
            + ".band .muted { color: #A8A8A8; font-size: 10px; }"
            + ".muted { color: #6b6b6b; font-size: 10px; }"
            + ".meta { width: 100%; margin-top: 14px; }"
            + ".meta .supply { text-align: right; vertical-align: top; }"
            + ".party-name { font-weight: bold; font-size: 13px; }"
            + ".items { width: 100%; border-collapse: collapse; margin-top: 16px; }"
            + ".items th { text-align: left; font-size: 10px; color: #FFFFFF; background-color: #17181A; padding: 6px; }"
            + ".items td { padding: 7px 6px; border-bottom: 1px solid #E2E2E2; }"
            + ".num { text-align: right; }"
            + ".idx { color: #9a9a9a; }"
            + ".totals { width: 280px; margin-left: auto; margin-top: 14px; border: 1px solid #17181A; border-collapse: collapse; }"
            + ".totals td { padding: 5px 10px; }"
            + ".totals .grand td { background-color: #F0F0EE; font-weight: bold; font-size: 14px; }"
            + ".balance { margin-top: 14px; text-align: right; font-weight: bold; padding: 10px 12px; }"
            + ".balance.due { color: #C0392B; background-color: #FBE4E1; }"
            + ".balance.paid { color: #1E8E3E; background-color: #E4F3E8; }"
            + ".notes { margin-top: 16px; color: #555555; }"
            + ".footer-note { margin-top: 16px; text-align: right; color: #555555; font-size: 11px; }"
            + ".brand { margin-top: 26px; padding-top: 10px; border-top: 1px solid #DDDDDD; text-align: center; color: #6b6b6b; font-size: 10px; }"
            + ".brand a { color: #015FAD; text-decoration: underline; }"
            + ".logo { width: 72px; height: 72px; margin-bottom: 10px; }"
            + ".qr { margin-left: auto; margin-top: 14px; border-collapse: collapse; }"
            + ".qr-img { width: 96px; height: 96px; }"
            + ".qr-label { padding-left: 10px; vertical-align: middle; font-weight: bold; font-size: 12px; }";
    }
}