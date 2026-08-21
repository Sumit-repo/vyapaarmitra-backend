package com.vyapaarmitra.api.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vyapaarmitra.api.common.ApiException;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

/**
 * Renders a well-formed XHTML string to PDF bytes (openhtmltopdf + PDFBox). Shared by
 * the bill / statement / overdue documents so there's one renderer for the mobile app
 * and the web dashboard.
 *
 * NOTE: openhtmltopdf wants strict XHTML and supports ~CSS 2.1 (no flexbox) — the HTML
 * builders lay out with tables, not flex. Devanagari (Hindi) glyphs need a bundled Unicode
 * font registered here; until then labels are English (see the builders).
 */
@Component
public class PdfRenderer {

    public byte[] render(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw ApiException.unprocessable("PDF_RENDER_FAILED", "Could not generate the PDF.");
        }
    }
}
