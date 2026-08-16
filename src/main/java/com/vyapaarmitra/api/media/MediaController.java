package com.vyapaarmitra.api.media;

import com.cloudinary.Transformation;
import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Image uploads (Cloudinary). Same shape as the Wellitica endpoints: multipart in,
 * a JSON URL out. Requires a valid access token (these live under /api/v1, which is
 * authenticated by default). Avatars replace the previous image; the old one is
 * deleted after the DB row is updated so a failure never orphans the live picture.
 */
@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final MediaService media;
    private final UserRepository users;

    public MediaController(MediaService media, UserRepository users) {
        this.media = media;
        this.users = users;
    }

    @PostMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAvatar(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestParam("avatar") MultipartFile file) {
        byte[] bytes = readImage(file);
        MediaService.Upload up = uploadOr500(bytes, "vyapaarmitra/avatars",
            new Transformation().width(400).height(400).crop("fill").gravity("face"));

        User user = users.findById(authUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String previous = user.getAvatarPublicId();
        user.setAvatarUrl(up.url());
        user.setAvatarPublicId(up.publicId());
        users.save(user);
        media.delete(previous);
        return Map.of("avatarUrl", up.url());
    }

    @DeleteMapping("/users/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@AuthenticationPrincipal AuthUser authUser) {
        User user = users.findById(authUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String previous = user.getAvatarPublicId();
        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        users.save(user);
        media.delete(previous);
    }

    /**
     * Upload a bill photo and get back its URL + public id. The caller (new-entry)
     * uploads first, then sends these on the create-entry request so the image is
     * linked to the entry it belongs to.
     */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAttachment(@RequestParam("file") MultipartFile file) {
        byte[] bytes = readImage(file);
        // Limit the long edge to 2000px (portrait bills included) so dense invoice text
        // stays readable/zoomable while files stay counter-connection friendly.
        MediaService.Upload up = uploadOr500(bytes, "vyapaarmitra/attachments",
            new Transformation().width(2000).height(2000).crop("limit").quality("auto:good"));
        return Map.of("url", up.url(), "publicId", up.publicId());
    }

    private MediaService.Upload uploadOr500(byte[] bytes, String folder, Transformation<?> t) {
        try {
            return media.uploadImage(bytes, folder, t);
        } catch (MediaException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED");
        }
    }

    private byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_FILE");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TYPE");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "READ_ERROR");
        }
    }
}
