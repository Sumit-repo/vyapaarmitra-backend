package com.vyapaarmitra.api.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.pdf.PdfRenderer;
import com.vyapaarmitra.api.records.RecordsService.StatementResponse;
import com.vyapaarmitra.api.records.RecordsService.StatementRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementPdfHtmlTest {

    private static StatementResponse statement(int months, List<StatementRow> rows) {
        return new StatementResponse(rows, new BigDecimal("500"), new BigDecimal("200"),
            new BigDecimal("300"), months);
    }

    private static StatementRow row(EntryType type, String amount, String note) {
        return new StatementRow(UUID.randomUUID(), UUID.randomUUID(), "Ramesh Sharma", type,
            new BigDecimal(amount), note, Instant.parse("2026-08-18T10:00:00Z"), new BigDecimal("300"));
    }

    @Test
    void rendersSummaryBothTypesAndPeriod() {
        String html = StatementPdfHtml.build(statement(3,
            List.of(row(EntryType.CREDIT, "500", null), row(EntryType.PAYMENT, "200", "UPI"))),
            "Bhagat Stores", true);
        assertTrue(html.contains("Statement"));
        assertTrue(html.contains("last 3 months"));
        assertTrue(html.contains("Bhagat Stores"));
        assertTrue(html.contains("Ramesh Sharma"));
        assertTrue(html.contains("Total credit"));
        assertTrue(html.contains("Net outstanding"));
        assertTrue(html.contains("UPI"));
    }

    @Test
    void emptyPeriodAndBrandingToggle() {
        String empty = StatementPdfHtml.build(statement(1, List.of()), "S", false);
        assertTrue(empty.contains("No entries in this period"));
        assertTrue(empty.contains("last 1 month"));
        assertFalse(empty.contains("Banaya gaya VyapaarMitra"));
    }

    @Test
    void rendersToValidPdf() {
        String html = StatementPdfHtml.build(statement(6,
            List.of(row(EntryType.CREDIT, "500", "note"))), "S", true);
        byte[] pdf = new PdfRenderer().render(html);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }
}
