package com.vyapaarmitra.api.dataimport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.ledger.EntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportControllerValidationTest {

    private static final UUID BRANCH = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void commitRejectsMissingBranch() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"OKCREDIT\",\"kind\":\"CUSTOMER\",\"contacts\":[" + contact("2026-08-09") + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.branchId").exists());
    }

    @Test
    void commitRejectsEmptyContacts() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"OKCREDIT\",\"kind\":\"CUSTOMER\",\"branchId\":\"" + BRANCH
                    + "\",\"contacts\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.contacts").exists());
    }

    @Test
    void commitRejectsContactWithoutName() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody("{\"name\":\"\",\"phone\":\"9122244192\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['contacts[0].name']").exists());
    }

    @Test
    void commitRejectsContactWithoutEntries() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody("{\"name\":\"Ramesh\",\"phone\":\"9122244192\",\"entries\":[]}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['contacts[0].entries']").exists());
    }

    @Test
    void commitRejectsBadPhone() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(entry("\"direction\":\"CREDIT\",\"amount\":2")
                    .replace("9122244192", "not-a-phone"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['contacts[0].phone']").exists());
    }

    @Test
    void commitRejectsUnknownDirection() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(entry("\"direction\":\"LOAN\",\"amount\":2"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void commitRejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(entry("\"direction\":\"CREDIT\",\"amount\":0"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['contacts[0].entries[0].amount']").exists());
    }

    @Test
    void commitRejectsTooManyDecimals() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(entry("\"direction\":\"CREDIT\",\"amount\":10.999"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['contacts[0].entries[0].amount']").exists());
    }

    @Test
    void commitRejectsUnknownSource() throws Exception {
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(entry("\"direction\":\"CREDIT\",\"amount\":2"))
                    .replace("OKCREDIT", "KHATABOOK")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void commitAcceptsValidRequest() throws Exception {
        Mockito.when(importService.commit(any(), any()))
            .thenReturn(new ImportDtos.CommitResponse(UUID.randomUUID(), 1, 0, 2));
        mockMvc.perform(post("/api/v1/imports/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(contact(LocalDate.of(2026, 8, 9).toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batchId").exists())
            .andExpect(jsonPath("$.partiesCreated").value(1))
            .andExpect(jsonPath("$.partiesMatched").value(0))
            .andExpect(jsonPath("$.entriesCreated").value(2));
    }

    @Test
    void parseAcceptsStatementPdf() throws Exception {
        Mockito.when(importService.parse(any(), any(), anyString(), any()))
            .thenReturn(parsedView());
        mockMvc.perform(multipart("/api/v1/imports/parse")
                .file(new MockMultipartFile("file", "statement.pdf", "application/pdf",
                    "%PDF-1.4 fake".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("OKCREDIT"))
            .andExpect(jsonPath("$.kind").value("CUSTOMER"))
            .andExpect(jsonPath("$.contacts[0].existingPartyId").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.contacts[0].duplicateCount").value(1));
    }

    @Test
    void parseRejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/parse"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"));
    }

    @Test
    void parseMapsUnprocessableStatementTo422() throws Exception {
        Mockito.when(importService.parse(any(), any(), anyString(), any()))
            .thenThrow(com.vyapaarmitra.api.common.ApiException.unprocessable("UNSUPPORTED_STATEMENT",
                "This PDF is not an OkCredit account statement."));
        mockMvc.perform(multipart("/api/v1/imports/parse")
                .file(new MockMultipartFile("file", "other.pdf", "application/pdf",
                    "%PDF-1.4 fake".getBytes())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_STATEMENT"));
    }

    // ------------------------------------------------------------------ json helpers

    /** Two entries (a credit and a payment) dated {@code date}. */
    private static String contact(String date) {
        return "{\"name\":\"Demo Customer\",\"phone\":\"9122244192\",\"entries\":["
            + "{\"date\":\"" + date + "\",\"direction\":\"CREDIT\",\"amount\":2,\"note\":\"Items on credit\"},"
            + "{\"date\":\"" + date + "\",\"direction\":\"PAYMENT\",\"amount\":1,\"note\":\"Cash paid\"}]}";
    }

    /** One entry with the given entry-level fragment, dated 2026-08-09. */
    private static String entry(String entryFragment) {
        return "{\"name\":\"Demo Customer\",\"phone\":\"9122244192\",\"entries\":["
            + "{\"date\":\"2026-08-09\"," + entryFragment + ",\"note\":\"Items on credit\"}]}";
    }

    private static String commitBody(String contactJson) {
        return "{\"source\":\"OKCREDIT\",\"kind\":\"CUSTOMER\",\"branchId\":\"" + BRANCH
            + "\",\"contacts\":[" + contactJson + "]}";
    }

    private static ImportDtos.ParsedStatementView parsedView() {
        return new ImportDtos.ParsedStatementView(ImportSource.OKCREDIT, ImportKind.CUSTOMER,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), new BigDecimal("1"), 1, 1, List.of(),
            List.of(new ImportDtos.ImportContactView("Demo Customer", "9122244192",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), new BigDecimal("2"),
                new BigDecimal("1"), 1, List.of(
                    new ImportDtos.ImportEntryView(LocalDate.of(2026, 8, 9), EntryType.CREDIT,
                        new BigDecimal("2"), "Items on credit")))));
    }
}