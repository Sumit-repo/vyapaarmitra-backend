package com.vyapaarmitra.api.pdf;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.vyapaarmitra.api.common.ApiException;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Uploads rendered PDF bytes to Cloudinary as a raw file and returns a public URL — the
 * shareable link mobile sends over WhatsApp / email (the stream endpoints stay for the
 * authenticated web view). Reuses the same Cloudinary bean as image media.
 *
 * NOTE: Cloudinary disables PDF/ZIP delivery by default — enable "Allow delivery of PDF
 * and ZIP files" in the account's Security settings or the returned URL 403s.
 */
@Component
public class PdfStorage {

    private final Cloudinary cloudinary;

    public PdfStorage(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** Upload the PDF under a stable public id (overwritten on re-share); returns its secure_url. */
    public String upload(byte[] pdf, String publicId) {
        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "pdfs",
            "resource_type", "raw",
            "public_id", publicId,
            "format", "pdf",
            "overwrite", true);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(pdf, options);
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw ApiException.unprocessable("PDF_UPLOAD_FAILED", "Could not upload the PDF.");
        }
    }
}
