package com.vyapaarmitra.api.billlayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.vyapaarmitra.api.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Builds the UPI "Scan &amp; Pay" QR as an inlined base64 PNG (openhtmltopdf loads
 * {@code data:} images, so the PDF stays a single self-contained artifact).
 *
 * zxing {@code core} only: it hands back a {@link BitMatrix}, so we rasterise it with
 * ImageIO ourselves instead of pulling in the javase artifact.
 */
final class BillQr {

    /** Module count of the rendered square, quiet zone included. */
    private static final int SIZE = 220;
    /** The bill's ink colour, so the QR reads as part of the document. */
    private static final int INK = 0xFF17181A;
    private static final int PAPER = 0xFFFFFFFF;

    private BillQr() {
    }

    /** {@code upi://pay?pa=…&pn=…&am=…&cu=INR} as a {@code data:image/png;base64,…} URI. */
    static String upiDataUri(String vpa, String payeeName, BigDecimal amount) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload(vpa, payeeName, amount),
                BarcodeFormat.QR_CODE, SIZE, SIZE,
                Map.of(EncodeHintType.MARGIN, 1,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(matrix));
        } catch (WriterException | IOException e) {
            throw ApiException.unprocessable("QR_RENDER_FAILED", "Could not generate the UPI QR code.");
        }
    }

    /**
     * The UPI deep link encoded in the QR — package-private so tests can pin the money.
     * The VPA goes in raw: it is a handle like {@code name@okaxis}, already safe in a
     * query component, and UPI apps match on the literal {@code name@psp}.
     */
    static String payload(String vpa, String payeeName, BigDecimal amount) {
        return "upi://pay?pa=" + (vpa == null ? "" : vpa.trim()) + "&pn=" + enc(payeeName)
            + "&am=" + plain(amount) + "&cu=INR";
    }

    /** Percent-encode a payload component; UPI apps prefer %20 over '+' for spaces. */
    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
            .replace("+", "%20");
    }

    /** Plain decimal amount — no grouping, no symbol; the UPI app re-formats it. */
    private static String plain(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static byte[] png(BitMatrix matrix) throws IOException {
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? INK : PAPER);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}