package com.vyapaarmitra.api.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vyapaarmitra.api.billlayout.BillPdfData;
import com.vyapaarmitra.api.billlayout.BillPreset;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import com.vyapaarmitra.api.pdf.PdfRenderer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillPdfHtmlTest {

    private static InvoiceResponse bill(BillType type, boolean interState, List<InvoiceItemJson> items,
                                        BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                                        String partyName) {
        BigDecimal tax = cgst.add(sgst).add(igst);
        return new InvoiceResponse(
            UUID.randomUUID(), UUID.randomUUID(), type,
            type == BillType.PAKKA ? "INV-0007" : "EST-0007",
            null, partyName, "+91 90000 00000", "07BBBBB1111B1Z5",
            "07AAAAA0000A1Z5", "Delhi", interState,
            items, BigDecimal.ZERO, new BigDecimal("400"), tax, cgst, sgst, igst,
            new BigDecimal("400").add(tax), BigDecimal.ZERO, new BigDecimal("400").add(tax),
            PaymentMode.CREDIT, BillStatus.UNPAID, "Thank you",
            null, Instant.parse("2026-08-20T10:00:00Z"), null, "Owner");
    }

    private static InvoiceItemJson item(String name, String hsn, BigDecimal taxRate) {
        return new InvoiceItemJson(name, new BigDecimal("1"), "bag", new BigDecimal("400"),
            hsn, taxRate, new BigDecimal("400"));
    }

    /**
     * CLASSIC view-model — the shape every bill rendered before bill-design (no logo, no
     * UPI QR, no footer note). The exact markup is pinned in the billlayout golden test.
     */
    private static String classic(InvoiceResponse bill, String shopName, boolean branding) {
        return BillPdfHtml.build(new BillPdfData(bill, shopName, null, null, null, branding,
            BillPreset.CLASSIC, false, false, null));
    }

    @Test
    void kacchaIsInvoiceWithoutGstin() {
        String html = classic(
            bill(BillType.KACCHA, false, List.of(item("Sugar", null, null)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Ramesh"),
            "Bhagat Stores", true);
        assertTrue(html.contains(">INVOICE<"));
        // Guardrail: a plain "Invoice" for an unregistered trader — never "Tax Invoice",
        // no GSTIN, no tax columns (that combo invites CGST §122 penalties).
        assertFalse(html.contains("TAX INVOICE"));
        assertFalse(html.contains("GSTIN"));
        assertFalse(html.contains("CGST"));
        assertTrue(html.contains("Bhagat Stores"));
        assertTrue(html.contains("Ramesh"));
        assertTrue(html.contains("Sugar"));
    }

    @Test
    void pakkaIsTaxInvoiceWithGstinHsnAndCgstSgst() {
        String html = classic(
            bill(BillType.PAKKA, false, List.of(item("Cement", "2523", new BigDecimal("18"))),
                new BigDecimal("36"), new BigDecimal("36"), BigDecimal.ZERO, "Ramesh"),
            "Bhagat Stores", true);
        assertTrue(html.contains("TAX INVOICE"));
        assertTrue(html.contains("07AAAAA0000A1Z5"));
        assertTrue(html.contains(">HSN<"));
        assertTrue(html.contains("2523"));
        assertTrue(html.contains("CGST"));
        assertTrue(html.contains("SGST"));
        assertFalse(html.contains(">IGST<"));
    }

    @Test
    void pakkaInterStateChargesIgst() {
        String html = classic(
            bill(BillType.PAKKA, true, List.of(item("Cement", "2523", new BigDecimal("18"))),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("72"), "Ramesh"),
            "Bhagat Stores", true);
        assertTrue(html.contains("IGST"));
        assertFalse(html.contains(">CGST<"));
    }

    @Test
    void brandingTogglesTheFooterWithAClickableLink() {
        InvoiceResponse b = bill(BillType.KACCHA, false, List.of(item("Sugar", null, null)),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "R");
        String branded = classic(b, "S", true);
        assertTrue(branded.contains("<a href=\"https://vyapaarmitra.vercel.app/\">VyapaarMitra</a>"));
        assertFalse(classic(b, "S", false).contains("VyapaarMitra"));
    }

    @Test
    void escapesUserText() {
        String html = classic(
            bill(BillType.KACCHA, false, List.of(item("Sugar", null, null)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "<b>x</b> & co"),
            "S", true);
        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt; &amp; co"));
        assertFalse(html.contains("<b>x</b>"));
    }

    @Test
    void producedHtmlRendersToAValidPdf() {
        // Hindi party/item + ₹ exercise the bundled Devanagari/Rupee font path.
        String html = classic(
            bill(BillType.PAKKA, false, List.of(item("सीमेंट", "2523", new BigDecimal("18"))),
                new BigDecimal("36"), new BigDecimal("36"), BigDecimal.ZERO, "रमेश शर्मा"),
            "भगत स्टोर्स", true);
        // The branded anchor must survive into the XHTML the renderer consumes.
        assertTrue(html.contains("<a href=\"https://vyapaarmitra.vercel.app/\">VyapaarMitra</a>"));
        byte[] pdf = new PdfRenderer().render(html);
        assertTrue(pdf.length > 0);
        // PDF magic number — proves the XHTML was well-formed enough to render.
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }
}