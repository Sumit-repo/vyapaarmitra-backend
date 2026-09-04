package com.vyapaarmitra.api.billlayout;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillLayoutRepository extends JpaRepository<BillLayout, UUID> {

    Optional<BillLayout> findByBusinessId(UUID businessId);
}