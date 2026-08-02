package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.BusinessMembershipView;
import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.RegisterRequest;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipService;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BusinessProvisioningService provisioningService;
    private final MembershipService membershipService;
    private final BranchAccessService branchAccessService;
    private final TokenIssuer tokenIssuer;

    public AuthService(UserRepository userRepository, BusinessRepository businessRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       BusinessProvisioningService provisioningService,
                       MembershipService membershipService, BranchAccessService branchAccessService,
                       TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.provisioningService = provisioningService;
        this.membershipService = membershipService;
        this.branchAccessService = branchAccessService;
        this.tokenIssuer = tokenIssuer;
    }

    /** Self-serve signup: stands up a new business and signs the owner straight in. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.badRequest("EMAIL_TAKEN", "An account with this email already exists.");
        }
        String branchName = request.branchName() == null || request.branchName().isBlank()
            ? "Main Branch"
            : request.branchName().trim();
        User owner = provisioningService.provision(request.businessName().trim(), branchName,
            request.ownerName().trim(), email, request.phone().trim(), request.password(),
            Boolean.TRUE.equals(request.defaulterNetworkConsent()));
        return tokenIssuer.issue(owner,
            membershipService.defaultActive(owner.getId(), owner.getDefaultBusinessId()));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        // Google-/OTP-only accounts have no password set.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        // Credentials identify the person; the business is their preferred (or newest) membership.
        return tokenIssuer.issue(user,
            membershipService.defaultActive(user.getId(), user.getDefaultBusinessId()));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        Integer version = claims.get("ver", Integer.class);
        if (version == null || version != user.getTokenVersion()) {
            throw ApiException.unauthorized("Session revoked, please log in again");
        }
        // Re-issue for the same business — and refuse if that membership was deactivated.
        UUID businessId = UUID.fromString(claims.get("bid", String.class));
        Membership membership = membershipService.require(user.getId(), businessId);
        return tokenIssuer.issue(user, membership);
    }

    /** Switch the active business: mint a fresh token pair scoped to another membership. */
    @Transactional(readOnly = true)
    public TokenResponse selectBusiness(AuthUser authUser, UUID businessId) {
        User user = userRepository.findById(authUser.id())
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        Membership membership = membershipService.require(user.getId(), businessId);
        return tokenIssuer.issue(user, membership);
    }

    /**
     * Pin the identity's default shop (the one login lands on). Validates the identity
     * still has an active membership there; the acting session is unchanged. Returns the
     * refreshed session payload so the client can reflect the new default immediately.
     */
    @Transactional
    public MeResponse setDefaultBusiness(AuthUser authUser, UUID businessId) {
        User user = userRepository.findById(authUser.id())
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        membershipService.require(user.getId(), businessId); // 401 if not an active membership
        user.setDefaultBusinessId(businessId);
        userRepository.save(user);
        // Reflect against the currently acting membership (default change doesn't switch shops).
        return tokenIssuer.toMe(user, membershipService.require(user.getId(), authUser.businessId()));
    }

    /**
     * Set the acting membership's preferred branch (null clears it). Validates branch access
     * for a non-null branch (owner: any active branch in the business; staff: an assigned one).
     */
    @Transactional
    public MeResponse setPreferredBranch(AuthUser authUser, UUID branchId) {
        User user = userRepository.findById(authUser.id())
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        if (branchId != null) {
            branchAccessService.assertBranchAccess(authUser, branchId);
        }
        membershipService.setPreferredBranch(user.getId(), authUser.businessId(), branchId);
        return tokenIssuer.toMe(user, membershipService.require(user.getId(), authUser.businessId()));
    }

    /** The businesses this identity can act in — powers the switcher. */
    @Transactional(readOnly = true)
    public List<BusinessMembershipView> memberships(AuthUser authUser) {
        return membershipService.activeFor(authUser.id()).stream()
            .map(m -> new BusinessMembershipView(
                m.getBusinessId(),
                businessRepository.findById(m.getBusinessId()).map(Business::getName).orElse(null),
                m.getRole()))
            .toList();
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        // Reflect the business the token is scoped to (from the active membership).
        Membership membership = membershipService.require(user.getId(), authUser.businessId());
        return tokenIssuer.toMe(user, membership);
    }
}
