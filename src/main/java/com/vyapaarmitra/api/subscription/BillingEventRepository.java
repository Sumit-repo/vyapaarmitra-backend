package com.vyapaarmitra.api.subscription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, UUID> {

    Optional<BillingEvent> findByGatewayAndGatewayEventId(String gateway, String gatewayEventId);
}
