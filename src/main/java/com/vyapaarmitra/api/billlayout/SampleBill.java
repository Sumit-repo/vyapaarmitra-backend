package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.invoice.BillStatus;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import com.vyapaarmitra.api.invoice.InvoiceItemJson;
import com.vyapaarmitra.api.invoice.PaymentMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * The synthetic bill behind {@code GET /bill-layout/preview}: a fixed PAKKA sample for
 * "Sharma Kirana Store" so the designer can preview a layout without touching the DB.
 * Deliberately deterministic (fixed ids, number, money and date) — the preview is the
 * same bytes every time for the same query string, which is what makes it cacheable.
 */
public final class SampleBill {

    private static final String SHOP = "Sharma Kirana Store";
    private static final String UPI_VPA = "sharma@okaxis";

    private SampleBill() {
    }

    /** The preview's view-model: the sample bill rendered in the requested design. */
    public static BillPdfData data(BillPreset layout, boolean showUpiQr, boolean showLogo,
                                   String footerNote) {
        return new BillPdfData(bill(), SHOP,
            showLogo ? placeholderLogo() : null,
            showUpiQr ? UPI_VPA : null,
            SHOP, true, layout, showUpiQr, showLogo, footerNote);
    }

    /** Two items, GST and a part payment — every block of every preset is exercised. */
    private static InvoiceResponse bill() {
        List<InvoiceItemJson> items = List.of(
            new InvoiceItemJson("Basmati Rice 5 kg", new BigDecimal("2"), "bag",
                new BigDecimal("550"), "1006", new BigDecimal("5"), new BigDecimal("1100")),
            new InvoiceItemJson("Toor Dal", new BigDecimal("3"), "kg",
                new BigDecimal("140"), "0713", new BigDecimal("5"), new BigDecimal("420")),
            new InvoiceItemJson("Sunflower Oil 1 L", new BigDecimal("2"), "pcs",
                new BigDecimal("165"), "1512", new BigDecimal("5"), new BigDecimal("330")));
        BigDecimal subtotal = new BigDecimal("1850");
        BigDecimal taxTotal = new BigDecimal("92.50");
        BigDecimal grandTotal = subtotal.add(taxTotal);
        BigDecimal received = new BigDecimal("500");
        return new InvoiceResponse(
            UUID.fromString("00000000-0000-0000-0000-00000000aa01"),
            UUID.fromString("00000000-0000-0000-0000-00000000aa02"),
            BillType.PAKKA, "INV-0042",
            UUID.fromString("00000000-0000-0000-0000-00000000aa03"),
            "Ramesh Kumar", "+91 90000 00000", "07BBBBB1111B1Z5",
            "07AAAAA0000A1Z5", "Delhi", false,
            items, BigDecimal.ZERO, subtotal, taxTotal,
            new BigDecimal("46.25"), new BigDecimal("46.25"), BigDecimal.ZERO,
            grandTotal, received, grandTotal.subtract(received),
            PaymentMode.UPI, BillStatus.PARTIAL, "Thank you, visit again!",
            null, Instant.parse("2026-09-03T04:30:00Z"),
            UUID.fromString("00000000-0000-0000-0000-00000000aa04"), "Sumit");
    }

    /**
     * Placeholder for the logo slot in the preview (a small storefront glyph, drawn with
     * primitives so no system font is needed). Real shops upload theirs through
     * {@code POST /attachments}.
     */
    static String placeholderLogo() {
        BufferedImage image = new BufferedImage(76, 76, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 76, 76);
            g.setColor(new Color(0x17, 0x18, 0x1A));
            g.fillRect(0, 0, 76, 16);            // awning
            g.drawRect(12, 16, 52, 48);          // shopfront
            g.fillRect(32, 42, 12, 22);          // door
            g.fillRect(20, 28, 12, 8);           // window
            g.fillRect(52, 28, 12, 8);           // window
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw ApiException.unprocessable("LOGO_RENDER_FAILED", "Could not render the sample logo.");
        }
    }
}