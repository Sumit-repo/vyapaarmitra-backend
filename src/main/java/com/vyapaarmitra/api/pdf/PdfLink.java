package com.vyapaarmitra.api.pdf;

/** A shareable public URL for a rendered PDF (returned by the {@code .../pdf/link} endpoints). */
public record PdfLink(String url) {
}
