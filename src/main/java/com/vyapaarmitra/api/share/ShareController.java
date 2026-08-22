package com.vyapaarmitra.api.share;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.share.ShareDtos.ShareTokenResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shopkeeper-facing share management. Creates (or reuses) a stable link to a customer's
 * ledger or a bill; the client builds the {@code /s/{token}} URL. Any member can create;
 * revoke is available if a link is misused.
 */
@RestController
@RequestMapping("/api/v1/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping("/ledger/{customerId}")
    public ShareTokenResponse ledger(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID customerId) {
        return new ShareTokenResponse(shareService.createLedgerShare(authUser, customerId));
    }

    @PostMapping("/bill/{invoiceId}")
    public ShareTokenResponse bill(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID invoiceId) {
        return new ShareTokenResponse(shareService.createBillShare(authUser, invoiceId));
    }

    @DeleteMapping("/{token}")
    public void revoke(@AuthenticationPrincipal AuthUser authUser, @PathVariable String token) {
        shareService.revoke(authUser, token);
    }
}
