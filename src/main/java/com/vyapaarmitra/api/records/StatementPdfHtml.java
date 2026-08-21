package com.vyapaarmitra.api.records;

import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.pdf.HtmlDoc;
import com.vyapaarmitra.api.records.RecordsService.StatementResponse;
import com.vyapaarmitra.api.records.RecordsService.StatementRow;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the XHTML for the shop's Statement PDF (the Reports 1/3/6-month credit/debit view).
 * Internal report — emailed to the shopkeeper / their CA, not to customers.
 */
public final class StatementPdfHtml {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private StatementPdfHtml() {
    }

    public static String build(StatementResponse st, String shopName, boolean branding) {
        StringBuilder rows = new StringBuilder();
        for (StatementRow r : st.rows()) {
            boolean credit = r.type() == EntryType.CREDIT;
            String note = r.note() == null || r.note().isBlank()
                ? "" : " <span class=\"muted\">- " + HtmlDoc.esc(r.note()) + "</span>";
            rows.append("<tr>")
                .append("<td>").append(r.entryAt() == null ? "" : DATE.format(r.entryAt().atZone(IST))).append("</td>")
                .append("<td>").append(HtmlDoc.esc(r.customerName())).append(note).append("</td>")
                .append("<td class=\"num credit\">").append(credit ? "+ " + HtmlDoc.rupees(r.amount()) : "").append("</td>")
                .append("<td class=\"num payment\">").append(credit ? "" : "- " + HtmlDoc.rupees(r.amount())).append("</td>")
                .append("<td class=\"num\">").append(HtmlDoc.rupees(r.balanceAfter())).append("</td>")
                .append("</tr>");
        }
        if (st.rows().isEmpty()) {
            rows.append("<tr><td colspan=\"5\" class=\"muted\">No entries in this period</td></tr>");
        }

        String body = "<table class=\"summary\"><tr>"
            + card("Total credit (udhaar)", HtmlDoc.rupees(st.totalCredit()))
            + card("Total payment", HtmlDoc.rupees(st.totalPayment()))
            + card("Net outstanding", HtmlDoc.rupees(st.closing()))
            + "</tr></table>"
            + "<table class=\"data\"><thead><tr>"
            + "<th>Date</th><th>Party</th><th class=\"num\">Credit</th>"
            + "<th class=\"num\">Payment</th><th class=\"num\">Balance</th>"
            + "</tr></thead><tbody>" + rows + "</tbody></table>";

        String label = st.months() == 1 ? "last 1 month" : "last " + st.months() + " months";
        return HtmlDoc.document("Statement", "Credit & payment - " + label, shopName, body, branding);
    }

    private static String card(String k, String v) {
        return "<td><div class=\"k\">" + k + "</div><div class=\"v\">" + v + "</div></td>";
    }
}
