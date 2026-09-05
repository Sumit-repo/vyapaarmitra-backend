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
 * The Pro presets: valid XHTML that openhtmltopdf can actually render, the toggles and
 * v2 design options they add, and the preview bytes the web designer consumes.
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
            BillLayoutOptions.standard(true, true, "Thank you, visit again!")));
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
            BillLayoutOptions.standard(true, false, null)));
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
            null, null, true, BillPreset.MINIMAL, BillLayoutOptions.standard(true, false, null));
        assertFalse(BillPdfHtml.build(noVpa).contains("<img class=\"qr-img\""));
        // Paid in full: a QR for ₹0 is noise.
        BillPdfData paid = new BillPdfData(BillSamples.paid(BillType.KACCHA), "S", null,
            "sharma@okaxis", "Sharma", true, BillPreset.MINIMAL,
            BillLayoutOptions.standard(true, false, null));
        assertFalse(BillPdfHtml.build(paid).contains("<img class=\"qr-img\""));
        assertTrue(BillPdfHtml.build(paid).contains("Paid in full"));
    }

    @Test
    void logoRendersOnlyWhenRequested() {
        BillPdfData withLogo = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S",
            "https://res.cloudinary.com/x/y.png", "sharma@okaxis", "Sharma", true,
            BillPreset.BOLD, BillLayoutOptions.standard(false, true, null));
        assertTrue(BillPdfHtml.build(withLogo).contains("<img class=\"logo\""));
        BillPdfData hidden = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S",
            "https://res.cloudinary.com/x/y.png", null, null, true,
            BillPreset.BOLD, BillLayoutOptions.standard(false, false, null));
        assertFalse(BillPdfHtml.build(hidden).contains("class=\"logo\""));
    }

    @Test
    void footerNoteIsEscaped() {
        BillPdfData note = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S", null,
            null, null, true, BillPreset.MINIMAL,
            BillLayoutOptions.standard(false, false, "<b>&</b>"));
        assertTrue(BillPdfHtml.build(note).contains("&lt;b&gt;&amp;&lt;/b&gt;"));
        assertFalse(BillPdfHtml.build(note).contains("<b>"));
    }

    @Test
    void previewRendersTheSampleBillToPdfBytes() {
        // Same call chain as POST /bill-layout/preview → the designer's iframe payload.
        byte[] pdf = RENDERER.render(BillPdfHtml.build(SampleBill.data(BillPreset.BOLD,
            BillLayoutOptions.standard(true, true, "GST extra"))));
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
        byte[] classic = RENDERER.render(BillPdfHtml.build(SampleBill.data(BillPreset.CLASSIC,
            BillLayoutOptions.defaults())));
        assertEquals("%PDF-", new String(classic, 0, 5, StandardCharsets.ISO_8859_1));
    }

    /** The pay band: "how much is due" and "how to clear it" sit in ONE row when both render. */

    @Test
    void payBandCombinesBalanceAndQrInEveryPreset() {
        for (BillPreset preset : BillPreset.values()) {
            String html = BillPdfHtml.build(data(preset,
                BillLayoutOptions.standard(true, false, null)));
            assertTrue(html.contains("<table class=\"pay\"><tr><td class=\"pay-due\"><div class=\"balance due\">"),
                preset + ": the balance should be the pay band's left cell");
            assertTrue(html.contains("<td class=\"pay-qr\"><table class=\"qr\">"),
                preset + ": the QR should be the pay band's right cell");
            assertTrue(html.contains(".pay { width: 100%"),
                preset + ": pay band styles should be emitted with the markup");
            byte[] pdf = RENDERER.render(html);
            assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                preset + ": pay band must still render to a PDF");
        }
    }

    @Test
    void payBandFallsBackToSeparateBlocksWhenOnlyOneRenders() {
        // Balance hidden → QR alone, no band markup or styles.
        BillLayoutOptions hideBalance = new BillLayoutOptions(true, false, null,
            null, null, null, false, true, false, null, null);
        String qrSolo = BillPdfHtml.build(data(BillPreset.MINIMAL, hideBalance));
        assertFalse(qrSolo.contains("<table class=\"pay\">"));
        assertTrue(qrSolo.contains("Scan &amp; Pay"));
        assertFalse(qrSolo.contains(".pay { width: 100%"));
        // No VPA → balance alone, no band (this is also the CLASSIC golden path).
        BillPdfData noVpa = new BillPdfData(BillSamples.bill(BillType.PAKKA), "S", null,
            null, null, true, BillPreset.CLASSIC, BillLayoutOptions.standard(true, false, null));
        String balanceSolo = BillPdfHtml.build(noVpa);
        assertFalse(balanceSolo.contains("<table class=\"pay\">"));
        assertTrue(balanceSolo.contains("Balance on khata:"));
    }

    /** v2 options — each change must be visible in the markup, not just accepted. */

    @Test
    void headingAlignOverrideIsAppendedOnlyWhenNotLeft() {
        String left = BillPdfHtml.build(data(BillPreset.MINIMAL,
            BillLayoutOptions.defaults()));
        assertFalse(left.contains(".head-left, .band-left { text-align: center; }"));
        String center = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, BillLayoutOptions.Align.CENTER,
                null, null, false, false, false, null, null)));
        assertTrue(center.contains(".head-left, .band-left { text-align: center; }"));
        assertTrue(center.contains(".head .logo, .band .logo { margin-left: auto; margin-right: auto; }"));
        byte[] pdf = RENDERER.render(center);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void footerAlignOverrideIsAppendedOnlyWhenNotRight() {
        String defaultNote = BillPdfHtml.build(data(BillPreset.MINIMAL,
            BillLayoutOptions.standard(false, false, "Thanks")));
        assertFalse(defaultNote.contains(".footer-note { text-align:"));
        String leftNote = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, "Thanks", null,
                BillLayoutOptions.Align.LEFT, null, false, false, false, null, null)));
        assertTrue(leftNote.contains(".footer-note { text-align: left; }"));
    }

    @Test
    void accentOverrideRecolorsTheRules() {
        String neutral = BillPdfHtml.build(data(BillPreset.MINIMAL, BillLayoutOptions.defaults()));
        assertFalse(neutral.contains("border-bottom-color: #015FAD"));
        String blue = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null,
                BillLayoutOptions.Accent.BLUE, false, false, false, null, null)));
        assertTrue(blue.contains(".head { border-bottom-color: #015FAD; }"));
        assertTrue(blue.contains(".band { background-color: #015FAD; }"));
        byte[] pdf = RENDERER.render(blue);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void hideBalanceDropsTheBalanceBlock() {
        String shown = BillPdfHtml.build(data(BillPreset.MINIMAL, BillLayoutOptions.defaults()));
        assertTrue(shown.contains("Balance on khata:"));
        String hidden = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null,
                false, true, false, null, null)));
        assertFalse(hidden.contains("Balance on khata:"));
        assertFalse(hidden.contains("Paid in full"));
    }

    @Test
    void hidePhoneDropsOnlyThePhoneLine() {
        String shown = BillPdfHtml.build(data(BillPreset.MINIMAL, BillLayoutOptions.defaults()));
        assertTrue(shown.contains(BillSamples.bill(BillType.PAKKA).partyPhone()));
        String hidden = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null,
                true, false, false, null, null)));
        assertFalse(hidden.contains(BillSamples.bill(BillType.PAKKA).partyPhone()));
        assertTrue(hidden.contains("Ramesh Kumar"));
    }

    @Test
    void hideBrandDropsThePoweredByFooter() {
        String shown = BillPdfHtml.build(data(BillPreset.MINIMAL, BillLayoutOptions.defaults()));
        assertTrue(shown.contains("VyapaarMitra"));
        String hidden = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null,
                false, false, true, null, null)));
        assertFalse(hidden.contains("VyapaarMitra"));
    }

    @Test
    void headingLabelReplacesTaxInvoiceOnPakkaOnly() {
        String pakka = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null,
                false, false, false, "उधार खाता", null)));
        assertTrue(pakka.contains(">उधार खाता<"));
        assertFalse(pakka.contains(">TAX INVOICE<"));
        // KACCHA must never inherit the custom label — it always prints INVOICE.
        BillPdfData kaccha = BillPdfData.of(BillSamples.bill(BillType.KACCHA), "S", null,
            null, null, BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null,
                false, false, false, "TAX INVOICE", null), true);
        assertTrue(BillPdfHtml.build(kaccha).contains(">INVOICE<"));
    }

    @Test
    void dateFormatVariantsPrintTheirPattern() {
        String iso = BillPdfHtml.build(data(BillPreset.MINIMAL, BillLayoutOptions.defaults()));
        assertTrue(iso.contains("3 Sep 2026"));
        String numeric = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null, false, false, false,
                null, BillLayoutOptions.DateFormat.DD_MM_YYYY)));
        assertTrue(numeric.contains("03-09-2026"));
        String slashed = BillPdfHtml.build(data(BillPreset.MINIMAL,
            new BillLayoutOptions(false, false, null, null, null, null, false, false, false,
                null, BillLayoutOptions.DateFormat.DD_MM_YYYY_SLASH)));
        assertTrue(slashed.contains("03/09/2026"));
    }

    @Test
    void normalizedOptionsResolveAbsentEnumsToThePreV2Look() {
        BillLayoutOptions o = new BillLayoutOptions(false, false, "  hi  ", null, null,
            null, false, false, false, "  ", null).normalized();
        assertEquals(BillLayoutOptions.Align.LEFT, o.headingAlign());
        assertEquals(BillLayoutOptions.Align.RIGHT, o.footerAlign());
        assertEquals(BillLayoutOptions.Accent.NEUTRAL, o.accent());
        assertEquals(BillLayoutOptions.DateFormat.D_MMM_YYYY, o.dateFormat());
        assertEquals("hi", o.footerNote());
        assertEquals(null, o.headingLabel());
        // Idempotent: normalising twice changes nothing.
        assertEquals(o, o.normalized());
    }
}