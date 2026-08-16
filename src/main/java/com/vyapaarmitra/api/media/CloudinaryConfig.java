package com.vyapaarmitra.api.media;

import com.cloudinary.Cloudinary;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudinary client, wired from the same env vars the Wellitica app uses so both
 * products can share one Cloudinary account. Values default to blank in dev; uploads
 * only work once the three vars are set on the deployed backend.
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    Cloudinary cloudinary(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
                          @Value("${CLOUDINARY_API_KEY:}") String apiKey,
                          @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
        return new Cloudinary(Map.of(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret,
            "secure", true));
    }
}
