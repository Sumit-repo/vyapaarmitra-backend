package com.vyapaarmitra.api.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin Cloudinary wrapper. Mirrors the Wellitica upload flow: the raw image bytes are
 * streamed to Cloudinary with a folder + transformation, and we keep the returned
 * secure_url (for display) and public_id (for later deletion).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** A stored image: its CDN URL and the Cloudinary id used to delete it later. */
    public record Upload(String url, String publicId) {
    }

    private final Cloudinary cloudinary;

    public MediaService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public Upload uploadImage(byte[] bytes, String folder, Transformation<?> transformation) {
        Map<String, Object> options = ObjectUtils.asMap(
            "folder", folder,
            "resource_type", "image",
            "transformation", transformation);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(bytes, options);
            return new Upload((String) result.get("secure_url"), (String) result.get("public_id"));
        } catch (IOException e) {
            throw new MediaException("Cloudinary upload failed", e);
        }
    }

    /** Best-effort delete — a stale image is harmless, so failures are logged, not thrown. */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.warn("Cloudinary delete failed for {}: {}", publicId, e.getMessage());
        }
    }
}
