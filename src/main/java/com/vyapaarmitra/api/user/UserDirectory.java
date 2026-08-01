package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch-resolves user ids to display names, business-scoped. Used to surface a
 * {@code createdByName} on ledger/supplier/invoice/reminder responses so owners
 * can see who recorded a credit or bill, without each domain service depending on
 * the fuller {@link UserService}.
 *
 * <p>Scoping goes through {@code memberships} (business_id lives there now, not on
 * {@code users}), so a name only resolves for an identity that actually belongs to
 * the business.
 */
@Service
public class UserDirectory {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    public UserDirectory(UserRepository userRepository, MembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /** Maps each (non-null) id in {@code ids} to a full name, dropping ids outside the business. */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesByBusiness(UUID businessId, Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Set<UUID> members = membershipRepository.findByBusinessIdOrderByCreatedAtAsc(businessId).stream()
            .map(Membership::getUserId)
            .collect(Collectors.toSet());
        wanted.retainAll(members);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (User u : userRepository.findAllById(wanted)) {
            names.put(u.getId(), u.getFullName());
        }
        return names;
    }

    /** Resolves a single id to a display name (null if unknown / outside the business). */
    @Transactional(readOnly = true)
    public String name(UUID businessId, UUID id) {
        if (id == null) {
            return null;
        }
        return namesByBusiness(businessId, Set.of(id)).get(id);
    }
}
