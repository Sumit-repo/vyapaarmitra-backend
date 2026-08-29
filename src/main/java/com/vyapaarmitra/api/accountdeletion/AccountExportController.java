package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.accountdeletion.AccountExportService.ExportFile;
import com.vyapaarmitra.api.auth.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /account/export} — a downloadable JSON copy of the caller's khata (businesses owned +
 * parties + ledger + bills). Stays reachable during the soft-delete grace via the freeze-guard
 * allowlist, so "get my data out" is honoured right up to purge. See docs/account-deletion.md.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountExportController {

    private final AccountExportService exportService;

    public AccountExportController(AccountExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export")
    public ResponseEntity<ExportFile> export(@AuthenticationPrincipal AuthUser authUser) {
        ExportFile file = exportService.export(authUser.id());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vyapaarmitra-export.json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(file);
    }
}
