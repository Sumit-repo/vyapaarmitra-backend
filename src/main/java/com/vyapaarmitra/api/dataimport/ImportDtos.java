package com.vyapaarmitra.api.dataimport;

import com.vyapaarmitra.api.ledger.EntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {
    }

    private static final String PHONE_PATTERN = "^\\+?[0-9][0-9\\s-]{5,14}$";
    private static final String PHONE_MESSAGE = "must be a valid phone number";

    /** One parsed transaction, as shown in the preview. */
    public record ImportEntryView(LocalDate date, EntryType direction, BigDecimal amount,
                                  String note) {
    }

    /** One contact (a would-be party) with everything the preview needs. */
    public record ImportContactView(String name, String phone, UUID existingPartyId,
                                    BigDecimal creditTotal, BigDecimal paymentTotal,
                                    long duplicateCount, List<ImportEntryView> entries) {
    }

    /** {@code POST /imports/parse} response — the client may edit it and send it to /commit. */
    public record ParsedStatementView(ImportSource source, ImportKind kind, LocalDate dateFrom,
                                      LocalDate dateTo, BigDecimal netBalance, int creditCount,
                                      int paymentCount, List<String> warnings,
                                      List<ImportContactView> contacts) {
    }

    public record CommitContact(@NotBlank @Size(max = 120) String name,
                                @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
                                @NotEmpty @Size(max = 1000) List<@Valid CommitEntry> entries) {
    }

    public record CommitImportRequest(@NotNull ImportSource source,
                                      @NotNull ImportKind kind,
                                      @NotNull UUID branchId,
                                      @NotEmpty @Size(max = 1000) List<@Valid CommitContact> contacts) {
    }

    public record CommitEntry(@NotNull LocalDate date,
                              @NotNull EntryType direction,
                              @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
                              @Size(max = 500) String note) {
    }

    /** {@code POST /imports/commit} response. */
    public record CommitResponse(UUID batchId, int partiesCreated, int partiesMatched,
                                 int entriesCreated) {
    }
}