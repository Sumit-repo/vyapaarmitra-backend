package com.vyapaarmitra.api.ledger;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.ledger.LedgerDtos.CreateEntryRequest;
import com.vyapaarmitra.api.ledger.LedgerDtos.EntryCreatedResponse;
import com.vyapaarmitra.api.ledger.LedgerDtos.EntryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryCreatedResponse create(@AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody CreateEntryRequest request) {
        return ledgerService.createEntry(authUser, request);
    }

    @GetMapping("/customers/{customerId}/ledger")
    public PageResponse<EntryResponse> ledger(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable UUID customerId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        return ledgerService.ledger(authUser, customerId, page, size);
    }
}
