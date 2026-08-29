package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.accountdeletion.AccountPurgeService.PurgeResult;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.config.AppProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server safety net / Cloud Scheduler trigger for the account purge. Authenticated by a
 * shared secret in {@code X-Internal-Token} (NOT a user JWT); the {@code /internal/**} path is
 * permitAll in the security chain precisely so this header check is the only gate. Idempotent.
 * See docs/account-deletion.md §5.
 */
@RestController
@RequestMapping("/api/v1/internal/accounts")
public class InternalPurgeController {

    private final AccountPurgeService purgeService;
    private final String expectedToken;

    public InternalPurgeController(AccountPurgeService purgeService, AppProperties props) {
        this.purgeService = purgeService;
        this.expectedToken = props.internal() == null ? null : props.internal().purgeToken();
    }

    @PostMapping("/purge")
    public Map<String, Integer> purge(@RequestHeader(value = "X-Internal-Token", required = false)
                                      String token) {
        requireInternalToken(token);
        PurgeResult result = purgeService.purgeExpired();
        return Map.of(
            "purgedUsers", result.purgedUsers(),
            "purgedBusinesses", result.purgedBusinesses());
    }

    /** Constant-time-ish shared-secret check. Refuses everything when no token is configured. */
    private void requireInternalToken(String token) {
        if (expectedToken == null || expectedToken.isBlank()
            || token == null || !expectedToken.equals(token)) {
            throw ApiException.unauthorized("Invalid internal token");
        }
    }
}
