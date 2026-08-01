package com.vyapaarmitra.api.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    private static final String PHONE_PATTERN = "^\\+?[0-9][0-9\\s-]{5,14}$";
    private static final String PHONE_MESSAGE = "must be a valid phone number";

    public record ItemRequest(@NotBlank @Size(max = 120) String name,
                              @NotNull @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal qty,
                              @Size(max = 16) String unit,
                              @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal rate,
                              @Size(max = 16) String hsn,
                              @DecimalMin("0.0") @DecimalMax("28.0") BigDecimal taxRate) {
    }

    public record CreateInvoiceRequest(@NotNull UUID branchId,
                                       @NotNull BillType billType,
                                       UUID partyCustomerId,
                                       @Size(max = 120) String partyName,
                                       @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String partyPhone,
                                       @Size(max = 20) String partyGstin,
                                       @Size(max = 20) String sellerGstin,
                                       @Size(max = 60) String placeOfSupply,
                                       Boolean interState,
                                       @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items,
                                       @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal discount,
                                       @NotNull PaymentMode paymentMode,
                                       @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal amountReceived,
                                       @Size(max = 1000) String notes) {
    }

    /** Compact list shape — small payloads for slow shop connections. */
    public record InvoiceListItem(UUID id, BillType billType, String number, String partyName,
                                  String partyPhone, BigDecimal grandTotal, BillStatus status,
                                  Instant createdAt) {

        public static InvoiceListItem from(Invoice i) {
            return new InvoiceListItem(i.getId(), i.getBillType(), i.getNumber(), i.getPartyName(),
                i.getPartyPhone(), i.getGrandTotal(), i.getStatus(), i.getCreatedAt());
        }
    }

    public record InvoiceResponse(UUID id, UUID branchId, BillType billType, String number,
                                  UUID partyCustomerId, String partyName, String partyPhone, String partyGstin,
                                  String sellerGstin, String placeOfSupply, boolean interState,
                                  List<InvoiceItemJson> items, BigDecimal discount, BigDecimal subtotal,
                                  BigDecimal taxTotal, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                                  BigDecimal grandTotal, BigDecimal amountReceived, BigDecimal balanceDue,
                                  PaymentMode paymentMode, BillStatus status, String notes,
                                  UUID ledgerEntryId, Instant createdAt,
                                  UUID createdBy, String createdByName) {

        /** {@code createdByName} is the resolved name of whoever raised the bill (business-local). */
        public static InvoiceResponse from(Invoice i, String createdByName) {
            BigDecimal tax = i.getTaxTotal();
            boolean inter = i.isInterState();
            BigDecimal cgst = inter ? BigDecimal.ZERO : tax.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal sgst = inter ? BigDecimal.ZERO : tax.subtract(cgst);
            BigDecimal igst = inter ? tax : BigDecimal.ZERO;
            BigDecimal balanceDue = i.getGrandTotal().subtract(i.getAmountReceived()).max(BigDecimal.ZERO);
            return new InvoiceResponse(i.getId(), i.getBranchId(), i.getBillType(), i.getNumber(),
                i.getPartyCustomerId(), i.getPartyName(), i.getPartyPhone(), i.getPartyGstin(),
                i.getSellerGstin(), i.getPlaceOfSupply(), inter, i.getItems(), i.getDiscount(),
                i.getSubtotal(), tax, cgst, sgst, igst, i.getGrandTotal(), i.getAmountReceived(),
                balanceDue, i.getPaymentMode(), i.getStatus(), i.getNotes(), i.getLedgerEntryId(),
                i.getCreatedAt(), i.getCreatedBy(), createdByName);
        }
    }
}
