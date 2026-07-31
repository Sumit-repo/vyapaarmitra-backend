package com.vyapaarmitra.api.membership;

import com.vyapaarmitra.api.common.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which business an authenticated identity acts in. Login lands on a
 * default membership; a session can then be switched to any other active one.
 * Shared by every auth path (password, OTP, Google) so they never drift.
 */
@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;

    public MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /** All businesses the identity can currently sign into. */
    @Transactional(readOnly = true)
    public List<Membership> activeFor(UUID userId) {
        return membershipRepository.findByUserIdAndActiveTrue(userId);
    }

    /**
     * The membership to land on at login: the most recently created active one
     * (feels like "last shop used"). Throws if the identity has no active access.
     */
    @Transactional(readOnly = true)
    public Membership defaultActive(UUID userId) {
        return activeFor(userId).stream()
            .max(Comparator.comparing(Membership::getCreatedAt))
            .orElseThrow(() -> ApiException.forbidden(
                "NO_BUSINESS_ACCESS", "This account has no active business access."));
    }

    /**
     * The identity's active membership in a specific business. Used on refresh and
     * on explicit business-switch; throws when the membership is gone or deactivated
     * (this is how removing a staffer cuts their access to that one shop).
     */
    @Transactional(readOnly = true)
    public Membership require(UUID userId, UUID businessId) {
        return membershipRepository.findByUserIdAndBusinessId(userId, businessId)
            .filter(Membership::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Session revoked, please log in again"));
    }
}
