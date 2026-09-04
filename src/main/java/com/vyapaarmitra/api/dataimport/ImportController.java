package com.vyapaarmitra.api.dataimport;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitImportRequest;
import com.vyapaarmitra.api.dataimport.ImportDtos.CommitResponse;
import com.vyapaarmitra.api.dataimport.ImportDtos.ParsedStatementView;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OkCredit statement import. Free for all plans — parse/commit carry no entitlement
 * or plan gate; the technical ceilings live in {@link ImportService}.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Parse an OkCredit statement PDF into a preview. The kind (customer vs supplier
     * statement) is read from the statement header; {@code branchId} narrows
     * existing-party matching, otherwise matching runs business-wide.
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedStatementView parse(@AuthenticationPrincipal AuthUser authUser,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) UUID branchId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Attach the statement PDF to import.");
        }
        try {
            return importService.parse(authUser, file.getBytes(), file.getOriginalFilename(), branchId);
        } catch (IOException e) {
            throw ApiException.badRequest("READ_ERROR", "Could not read the uploaded file.");
        }
    }

    /**
     * Commit the (possibly edited) parsed statement. Single transaction: party
     * resolution + ledger replay + the import-batch row all succeed or none of it lands.
     */
    @PostMapping("/commit")
    public CommitResponse commit(@AuthenticationPrincipal AuthUser authUser,
                                 @Valid @RequestBody CommitImportRequest request) {
        return importService.commit(authUser, request);
    }
}