package com.vyapaarmitra.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, Jwt jwt, Cors cors, Bootstrap bootstrap,
                            Google google, Mail mail) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Bootstrap(String ownerEmail, String ownerPassword, String ownerName,
                            String businessName, String branchName) {
    }

    /** Accepted Google OAuth client IDs (audiences): web + Android + iOS clients. */
    public record Google(List<String> clientIds) {
    }

    /** Transactional email. When resendApiKey is blank, codes are logged instead of sent (dev). */
    public record Mail(String from, String resendApiKey, int otpTtlMinutes) {
    }
}
