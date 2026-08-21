package com.vyapaarmitra.api.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared XHTML helpers + branded A4 chrome for the internal report PDFs (statement, overdue).
 * Table-based layout (openhtmltopdf has no flexbox); ASCII-safe text until a Unicode font
 * (Rupee sign + Devanagari) is bundled with {@link PdfRenderer}.
 */
public final class HtmlDoc {

    private static final String BRAND_TAGLINE =
        "Banaya gaya VyapaarMitra se - free khata &amp; GST billing for shops";

    private HtmlDoc() {
    }

    /** Wrap a report body in the branded document shell. {@code body} is already-escaped HTML. */
    public static String document(String title, String subtitle, String shopName,
                                  String body, boolean branding) {
        String brand = branding ? "<div class=\"brand\">" + BRAND_TAGLINE + "</div>" : "";
        String sub = subtitle == null || subtitle.isBlank()
            ? "" : "<div class=\"muted\">" + esc(subtitle) + "</div>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html><head><meta charset=\"utf-8\" /><style>" + css() + "</style></head><body>"
            + "<table class=\"head\"><tr>"
            + "<td><div class=\"shop-name\">" + esc(shopName) + "</div></td>"
            + "<td class=\"right\"><div class=\"h\">" + esc(title) + "</div>" + sub + "</td>"
            + "</tr></table>"
            + body + brand
            + "</body></html>";
    }

    private static String css() {
        return "* { box-sizing: border-box; }"
            + "body { font-family: sans-serif; color: #17181A; font-size: 12px; }"
            + ".head { width: 100%; border-bottom: 2px solid #17181A; }"
            + ".head .right { text-align: right; }"
            + ".shop-name { font-size: 20px; font-weight: bold; }"
            + ".head .h { font-size: 17px; font-weight: bold; }"
            + ".muted { color: #6b6b6b; font-size: 10px; }"
            + "h3 { margin: 16px 0 4px; font-size: 13px; }"
            + "table.data { width: 100%; border-collapse: collapse; margin-top: 10px; }"
            + "table.data th { text-align: left; font-size: 10px; color: #6b6b6b; border-bottom: 1px solid #ddd; padding: 5px 6px; }"
            + "table.data td { padding: 6px; border-bottom: 1px solid #f0f0f0; }"
            + ".num { text-align: right; }"
            + ".credit { color: #C0392B; }"
            + ".payment { color: #1E8E3E; }"
            + ".summary { width: 100%; margin-top: 14px; }"
            + ".summary td { border: 1px solid #eee; padding: 8px 12px; width: 33%; }"
            + ".summary .k { font-size: 10px; color: #6b6b6b; }"
            + ".summary .v { font-size: 15px; font-weight: bold; }"
            + ".notes-col { width: 150px; }"
            + ".brand { margin-top: 26px; padding-top: 10px; border-top: 1px dashed #ccc; text-align: center; color: #6b6b6b; font-size: 10px; }";
    }

    /** Escape the five XML-significant chars so shop/customer/note text can't break the doc. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /** Indian-grouped rupees, e.g. 130945 → "Rs 1,30,945" (whole rupees; ASCII until a font is bundled). */
    public static String rupees(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        boolean neg = v.signum() < 0;
        String digits = v.abs().setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder grouped = new StringBuilder();
        int len = digits.length();
        for (int idx = 0; idx < len; idx++) {
            int fromEnd = len - idx;
            grouped.append(digits.charAt(idx));
            if (fromEnd > 3 && (fromEnd - 3) % 2 == 0 && fromEnd != len) grouped.append(',');
        }
        return (neg ? "-" : "") + "Rs " + grouped;
    }
}
