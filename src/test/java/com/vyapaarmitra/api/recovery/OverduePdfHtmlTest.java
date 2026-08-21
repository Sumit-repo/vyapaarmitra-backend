package com.vyapaarmitra.api.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vyapaarmitra.api.pdf.PdfRenderer;
import com.vyapaarmitra.api.recovery.RecoveryService.RecoveryItem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverduePdfHtmlTest {

    private static RecoveryItem item(String name, String phone, String amount, long days) {
        return new RecoveryItem(UUID.randomUUID(), UUID.randomUUID(), name, phone,
            new BigDecimal(amount), null, days, 0, null);
    }

    private static final List<RecoveryItem> ITEMS = List.of(
        item("Farhan Ali", "+91 90000 00000", "18000", 92),
        item("Suresh Patel", "", "5000", 40),
        item("Meena Tiwari", "", "800", 3));

    @Test
    void groupsIntoAgingBucketsWithTotals() {
        String html = OverduePdfHtml.build(ITEMS, "Bhagat Stores", true);
        assertTrue(html.contains("90+ days"));
        assertTrue(html.contains("30-90 days"));
        assertTrue(html.contains("0-7 days"));
        assertFalse(html.contains("7-30 days")); // no party there
        assertTrue(html.contains("Total at risk"));
        assertTrue(html.contains("Farhan Ali"));
        assertTrue(html.contains(">Notes<"));
    }

    @Test
    void emptyListAndEscaping() {
        assertTrue(OverduePdfHtml.build(List.of(), "S", true).contains("No overdue accounts"));
        String esc = OverduePdfHtml.build(List.of(item("<b>x</b>", "", "100", 5)), "S", true);
        assertTrue(esc.contains("&lt;b&gt;x&lt;/b&gt;"));
    }

    @Test
    void rendersToValidPdf() {
        byte[] pdf = new PdfRenderer().render(OverduePdfHtml.build(ITEMS, "S", true));
        assertTrue(pdf.length > 0);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }
}
