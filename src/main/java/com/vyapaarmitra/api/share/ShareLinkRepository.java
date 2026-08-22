package com.vyapaarmitra.api.share;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByToken(String token);

    /** Reuse an existing, non-revoked link for the same customer/bill instead of piling up rows. */
    Optional<ShareLink> findFirstByBusinessIdAndTypeAndCustomerIdAndRevokedFalse(
        UUID businessId, ShareType type, UUID customerId);

    Optional<ShareLink> findFirstByBusinessIdAndTypeAndInvoiceIdAndRevokedFalse(
        UUID businessId, ShareType type, UUID invoiceId);
}
