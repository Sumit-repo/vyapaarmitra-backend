package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.GoogleAuthRequest;
import com.vyapaarmitra.api.auth.AuthDtos.GoogleAuthResponse;
import com.vyapaarmitra.api.auth.GoogleTokenVerifier.GoogleIdentity;
import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google sign-in. Resolves the identity to an account by Google subject, falling
 * back to email (linking the subject on first Google use). A brand-new user needs
 * a business name to provision their shop; when none is supplied we ask the client
 * to collect one via {@code needsOnboarding}.
 */
@Service
public class GoogleAuthService {

    private final GoogleTokenVerifier verifier;
    private final UserRepository userRepository;
    private final BusinessProvisioningService provisioningService;
    private final TokenIssuer tokenIssuer;

    public GoogleAuthService(GoogleTokenVerifier verifier, UserRepository userRepository,
                             BusinessProvisioningService provisioningService, TokenIssuer tokenIssuer) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public GoogleAuthResponse signIn(GoogleAuthRequest req) {
        GoogleIdentity identity = verifier.verify(req.idToken());

        // 1) Already linked to this Google account.
        User linked = userRepository.findByGoogleSub(identity.sub()).filter(User::isActive).orElse(null);
        if (linked != null) {
            return session(linked);
        }

        // 2) Existing password/OTP account with the same email — link Google to it.
        User byEmail = userRepository.findByEmailIgnoreCase(identity.email())
            .filter(User::isActive).orElse(null);
        if (byEmail != null) {
            byEmail.setGoogleSub(identity.sub());
            byEmail.setEmailVerified(true);
            return session(byEmail);
        }

        // 3) First-time user: need a business name before we can provision a shop.
        String businessName = req.businessName() == null ? null : req.businessName().trim();
        if (businessName == null || businessName.isBlank()) {
            return new GoogleAuthResponse(true, identity.email(), identity.name(), null);
        }
        String branchName = req.branchName() == null || req.branchName().isBlank()
            ? "Main Branch" : req.branchName().trim();
        String ownerName = identity.name() != null && !identity.name().isBlank()
            ? identity.name() : identity.email();
        User owner = provisioningService.provisionOAuth(businessName, branchName, ownerName,
            identity.email(), identity.sub());
        return session(owner);
    }

    private GoogleAuthResponse session(User user) {
        return new GoogleAuthResponse(false, user.getEmail(), user.getFullName(), tokenIssuer.issue(user));
    }
}
