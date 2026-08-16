package com.vyapaarmitra.api.media;

/** Thrown when an image upload to Cloudinary fails; mapped to 500 by the controller. */
public class MediaException extends RuntimeException {

    public MediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
