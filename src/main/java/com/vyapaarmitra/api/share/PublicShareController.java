package com.vyapaarmitra.api.share;

import com.vyapaarmitra.api.share.ShareDtos.ShareLedgerResponse;
import com.vyapaarmitra.api.share.ShareDtos.ShareMetaResponse;
import com.vyapaarmitra.api.share.ShareDtos.VerifyRequest;
import com.vyapaarmitra.api.share.ShareDtos.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated customer viewer endpoints (permitAll — see SecurityConfig). Metadata is
 * PII-free; everything else needs the short-lived view token minted by {@code /verify} after
 * the phone last-4 check. The bill PDF is streamed on demand (no Cloudinary URL exposed).
 */
@RestController
@RequestMapping("/api/v1/public/share")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    public ShareMetaResponse meta(@PathVariable String token) {
        return shareService.meta(token);
    }

    @PostMapping("/{token}/verify")
    public VerifyResponse verify(@PathVariable String token, @Valid @RequestBody VerifyRequest request) {
        return new VerifyResponse(shareService.verify(token, request.last4()));
    }

    @GetMapping("/{token}/ledger")
    public ShareLedgerResponse ledger(@PathVariable String token, @RequestParam("vt") String viewToken) {
        return shareService.ledger(token, viewToken);
    }

    @GetMapping("/{token}/bill.pdf")
    public ResponseEntity<byte[]> billPdf(@PathVariable String token, @RequestParam("vt") String viewToken) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bill.pdf\"")
            .body(shareService.billPdf(token, viewToken));
    }
}
