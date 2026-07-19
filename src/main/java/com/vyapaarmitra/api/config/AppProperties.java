package com.vyapaarmitra.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, Jwt jwt, Cors cors, Bootstrap bootstrap) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Bootstrap(String ownerEmail, String ownerPassword, String ownerName,
                            String businessName, String branchName) {
    }
}
