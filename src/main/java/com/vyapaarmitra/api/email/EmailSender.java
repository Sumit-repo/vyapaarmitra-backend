package com.vyapaarmitra.api.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaarmitra.api.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email via Resend's HTTP API using the JDK HttpClient — no
 * extra Maven dependency, keeping the backend cloud-portable. When no API key is
 * configured the code path degrades to logging the message body, so local/dev
 * environments work without a provider (the one-time code shows up in the logs).
 */
@Slf4j
@Service
public class EmailSender {

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final AppProperties.Mail mail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmailSender(AppProperties props, ObjectMapper objectMapper) {
        this.mail = props.mail();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /** True when a real provider is configured; otherwise emails are only logged. */
    public boolean isLiveDelivery() {
        return mail.resendApiKey() != null && !mail.resendApiKey().isBlank();
    }

    public void send(String to, String subject, String htmlBody) {
        if (!isLiveDelivery()) {
            log.warn("[email] RESEND_API_KEY not set — not sending. to={} subject={}\n{}",
                to, subject, htmlBody);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "from", mail.from(),
                "to", to,
                "subject", subject,
                "html", htmlBody));
            HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + mail.resendApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("[email] Resend returned {} for to={}: {}", response.statusCode(), to, response.body());
                throw new IllegalStateException("Email delivery failed");
            }
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[email] Failed to send to {}", to, e);
            throw new IllegalStateException("Email delivery failed", e);
        }
    }
}
