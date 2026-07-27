package com.vyapaarmitra.api.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Audit + idempotency record for a gateway webhook. The unique
 * (gateway, gateway_event_id) constraint means an event is never processed twice
 * even if the gateway retries.
 */
@Entity
@Table(name = "billing_events")
@Getter
@Setter
@NoArgsConstructor
public class BillingEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "gateway_event_id", nullable = false)
    private String gatewayEventId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private Instant receivedAt;
}
