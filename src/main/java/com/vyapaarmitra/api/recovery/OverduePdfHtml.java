package com.vyapaarmitra.api.recovery;

import com.vyapaarmitra.api.pdf.HtmlDoc;
import com.vyapaarmitra.api.recovery.RecoveryService.RecoveryItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongPredicate;

/**
 * Builds the XHTML for the overdue / recovery PDF — the shopkeeper's own "who owes me" sheet.
 * Internal (never shared with customers): groups by aging bucket and leaves a blank Notes
 * column to jot remarks (e.g. "phone off", "promised Tuesday").
 */
public final class OverduePdfHtml {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private record Bucket(String label, LongPredicate test) {
    }

    private static final List<Bucket> BUCKETS = List.of(
        new Bucket("90+ days", d -> d >= 90),
        new Bucket("30-90 days", d -> d >= 30 && d < 90),
        new Bucket("7-30 days", d -> d >= 7 && d < 30),
        new Bucket("0-7 days", d -> d < 7));

    private OverduePdfHtml() {
    }

    public static String build(List<RecoveryItem> items, String shopName, boolean branding) {
        BigDecimal totalAtRisk = items.stream()
            .map(i -> i.amountDue() == null ? BigDecimal.ZERO : i.amountDue())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sections = new StringBuilder();
        for (Bucket b : BUCKETS) {
            List<RecoveryItem> inBucket = items.stream()
                .filter(i -> b.test().test(i.overdueDays()))
                .sorted(Comparator.comparingLong(RecoveryItem::overdueDays).reversed())
                .toList();
            if (inBucket.isEmpty()) continue;
            sections.append("<h3>").append(b.label())
                .append(" <span class=\"muted\">(").append(inBucket.size()).append(")</span></h3>")
                .append("<table class=\"data\"><thead><tr>")
                .append("<th>Customer</th><th>Phone</th><th class=\"num\">Days</th>")
                .append("<th class=\"num\">Outstanding</th><th class=\"notes-col\">Notes</th>")
                .append("</tr></thead><tbody>");
            for (RecoveryItem i : inBucket) {
                sections.append("<tr>")
                    .append("<td>").append(HtmlDoc.esc(i.name())).append("</td>")
                    .append("<td>").append(HtmlDoc.esc(i.phone())).append("</td>")
                    .append("<td class=\"num\">").append(i.overdueDays()).append("</td>")
                    .append("<td class=\"num credit\">").append(HtmlDoc.rupees(i.amountDue())).append("</td>")
                    .append("<td class=\"notes-col\"></td>")
                    .append("</tr>");
            }
            sections.append("</tbody></table>");
        }

        String body = "<table class=\"summary\"><tr>"
            + "<td><div class=\"k\">Total at risk</div><div class=\"v\">" + HtmlDoc.rupees(totalAtRisk) + "</div></td>"
            + "<td><div class=\"k\">Overdue accounts</div><div class=\"v\">" + items.size() + "</div></td>"
            + "</tr></table>"
            + (sections.length() == 0 ? "<p class=\"muted\">No overdue accounts - all clear.</p>" : sections);

        String today = DATE.format(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        return HtmlDoc.document("Overdue accounts", "As of " + today, shopName, body, branding);
    }
}
