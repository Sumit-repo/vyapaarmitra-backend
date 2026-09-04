package com.vyapaarmitra.api.billlayout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

/**
 * Request/response shapes for {@code /api/v1/bill-layout}. The response carries the
 * preset catalog ({@code available}) so the web/mobile designer never hardcodes it.
 */
public final class BillLayoutDtos {

    private BillLayoutDtos() {
    }

    /** One preset card in the designer. {@code pro} marks the paywalled ones. */
    public record PresetView(String key, String label, boolean pro) {

        public static PresetView from(BillPreset preset) {
            return new PresetView(preset.name(), preset.label(), preset.pro());
        }
    }

    public record OptionsView(boolean showUpiQr, boolean showLogo, String footerNote,
                              String headingAlign, String footerAlign, String accent,
                              boolean hidePhone, boolean hideBalance, boolean hideBrand,
                              String headingLabel, String dateFormat) {

        public static OptionsView from(BillLayoutOptions options) {
            return new OptionsView(options.showUpiQr(), options.showLogo(), options.footerNote(),
                name(options.headingAlign()), name(options.footerAlign()), name(options.accent()),
                options.hidePhone(), options.hideBalance(), options.hideBrand(),
                options.headingLabel(), name(options.dateFormat()));
        }

        /** Enums render as their wire names (or null when unset — the pre-v2 default). */
        private static String name(Enum<?> value) {
            return value == null ? null : value.name();
        }
    }

    /**
     * {@code layout} + toggles + the shop's logo + the preset catalog, in that order.
     * Returned by both GET and PUT.
     */
    public record BillLayoutView(String layout, OptionsView options, String logoUrl,
                                 List<PresetView> available) {

        public static BillLayoutView of(BillPreset layout, BillLayoutOptions options, String logoUrl) {
            return new BillLayoutView(layout.name(), OptionsView.from(options), logoUrl,
                Arrays.stream(BillPreset.values()).map(PresetView::from).toList());
        }
    }

    /**
     * Full replace of the shop's bill design. {@code logoUrl}/{@code logoPublicId} are
     * optional: omit both to leave the logo untouched (an upload round-trip through
     * {@code POST /attachments} sets both). Sending an empty {@code logoUrl} clears it.
     */
    public record UpsertBillLayoutRequest(@NotNull BillPreset layout,
                                          @NotNull @Valid BillLayoutOptions options,
                                          @Size(max = 1024) String logoUrl,
                                          @Size(max = 255) String logoPublicId) {
    }

    /** Body of {@code POST /bill-layout/preview} — the designer's live render request. */
    public record PreviewRequest(@NotNull BillPreset layout, @Valid BillLayoutOptions options) {
    }

    /**
     * Whether a request needs the BILL_LAYOUTS entitlement: any non-classic preset, or
     * any option off its pre-v2 default (absent enum = default, absent hide-flag = off).
     * {@code footerNote} counts when it carries text.
     */
    public static boolean requiresPro(BillPreset layout, BillLayoutOptions o) {
        boolean noted = o.footerNote() != null && !o.footerNote().isBlank();
        boolean labelled = o.headingLabel() != null && !o.headingLabel().isBlank();
        return layout != BillPreset.CLASSIC || o.showUpiQr() || o.showLogo() || noted
            || isSet(o.headingAlign(), BillLayoutOptions.Align.LEFT)
            || isSet(o.footerAlign(), BillLayoutOptions.Align.RIGHT)
            || isSet(o.accent(), BillLayoutOptions.Accent.NEUTRAL)
            || isSet(o.dateFormat(), BillLayoutOptions.DateFormat.D_MMM_YYYY)
            || o.hidePhone() || o.hideBalance() || o.hideBrand()
            || labelled;
    }

    /** True when the enum is present and not the default (absent = default = free). */
    private static boolean isSet(Enum<?> value, Enum<?> fallback) {
        return value != null && value != fallback;
    }
}