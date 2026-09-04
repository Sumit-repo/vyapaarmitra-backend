package com.vyapaarmitra.api.billlayout;

import static com.vyapaarmitra.api.pdf.HtmlDoc.rupees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vyapaarmitra.api.invoice.BillPdfHtml;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.pdf.PdfRenderer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The Pro presets: valid XHTML that openhtmltopdf can actually render, the toggles they
 * add, and the preview bytes the web designer's iframe consumes.
 */
class BillPresetRenderTest {

    private static final PdfRenderer RENDERER = new PdfRenderer();

    private static BillPdfData data(BillPreset preset, BillLayoutOptions options) {
        return BillPdfData.of(BillSamples.bill(BillType.PAKKA), "Sharma Kirana Store",
            null, "sharma@okaxis", "Sharma Kirana Store", preset, options, true);
    }

    @ParameterizedTest
    @EnumSource(value = BillPreset.class, names = {"MINIMAL", "BOLD"})
    void proPresetsRenderToAValidPdf(BillPreset preset) {
        String html = BillPdfHtml.build(data(preset,
            new BillLayoutOptions(true, true, "Thank you, visit again!")));
        // Every preset carries the same content — only the chrome changes.
        assertTrue(html.contains(">TAX INVOICE<"));
        assertTrue(html.contains("Sharma Kirana Store"));
        assertTrue(html.contains("Cement Bag"));
        assertTrue(html.contains(">CGST<"));
        assertTrue(html.contains("Balance on khata:"));
        assertTrue(html.contains("Scan &amp; Pay"));
        assertTrue(html.contains("Thank you, visit again!"));
        assertTrue(html.contains("data:image/png;base64,"));
        byte[] pdf = RENDERER.render(html);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void upiQrCarriesThePayeeAmountAndCurrency() {
        String html = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(true, false, null)));
        assertTrue(html.contains("<img class=\"qr-img\" src=\"data:image/png;base64,"));
        assertTrue(html.contains("Scan &amp; Pay " + rupees(
            BillSamples.bill(BillType.PAKKA).balanceDue())));
        assertEquals("upi://pay?pa=sharma@okaxis&pn=Sharma%20Kirana%20Store&am=662&cu=INR",
            BillQr.payload("sharma@okaxis", "Sharma Kirana Store", new BigDecimal("662")));
    }

    @Test
    void upiQrIsSkippedWithoutAVpaOrWhenNothingIsDue() {
        // No VPA configured: no QR, no half-working payment instruction.
        BillPdfData noVpa = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S", null,
            null, null, true, BillPreset.MINIMAL, true, false, null);
        assertFalse(BillPdfHtml.build(noVpa).contains("<img class=\"qr-img\""));
        // Paid in full: a QR for ₹0 is noise.
        BillPdfData paid = new BillPdfData(BillSamples.paid(BillType.KACCHA), "S", null,
            "sharma@okaxis", "Sharma", true, BillPreset.MINIMAL, true, false, null);
        assertFalse(BillPdfHtml.build(paid).contains("<img class=\"qr-img\""));
        assertTrue(BillPdfHtml.build(paid).contains("Paid in full"));
    }

    @Test
    void logoRendersOnlyWhenRequested() {
        BillPdfData withLogo = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S",
            "https://res.cloudinary.com/x/y.png", "sharma@okaxis", "Sharma", true,
            BillPreset.BOLD, false, true, null);
        assertTrue(BillPdfHtml.build(withLogo).contains("<img class=\"logo\""));
        BillPdfData hidden = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S",
            "https://res.cloudinary.com/x/y.png", null, null, true,
            BillPreset.BOLD, false, false, null);
        assertFalse(BillPdfHtml.build(hidden).contains("class=\"logo\""));
    }

    @Test
    void footerNoteIsEscaped() {
        BillPdfData note = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S", null,
            null, null, true, BillPreset.MINIMAL, false, false, "<b>&</b>");
        assertTrue(BillPdfHtml.build(note).contains("&lt;b&gt;&amp;&lt;/b&gt;"));
        assertFalse(BillPdfHtml.build(note).contains("<b>"));
    }

    @Test
    void previewRendersTheSampleBillToPdfBytes() {
        // Same call chain as GET /bill-layout/preview → the designer's iframe payload.
        byte[] pdf = RENDERER.render(
            BillPdfHtml.build(SampleBill.data(BillPreset.BOLD, true, true, "GST extra")));
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
        byte[] classic = RENDERER.render(
            BillPdfHtml.build(SampleBill.data(BillPreset.CLASSIC, false, false, null)));
        assertEquals("%PDF-", new String(classic, 0, 5, StandardCharsets.ISO_8859_1));
    }
}