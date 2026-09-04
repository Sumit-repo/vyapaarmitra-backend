package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.BillLayoutView;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.UpsertBillLayoutRequest;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.invoice.BillPdfHtml;
import com.vyapaarmitra.api.media.MediaService;
import com.vyapaarmitra.api.pdf.PdfRenderer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/upsert side of the shop's bill design. Absent row ⇒ CLASSIC with default options
 * (read-only default, never seeded) so a shop that never opens the designer keeps the
 * bill it has always printed.
 */
@Service
public class BillLayoutService {

    private final BillLayoutRepository layouts;
    private final BusinessRepository businesses;
    private final MediaService media;
    private final PdfRenderer pdfRenderer;
    private final BillPreviewCache previewCache;

    public BillLayoutService(BillLayoutRepository layouts,
                             BusinessRepository businesses,
                             MediaService media,
                             PdfRenderer pdfRenderer,
                             BillPreviewCache previewCache) {
        this.layouts = layouts;
        this.businesses = businesses;
        this.media = media;
        this.pdfRenderer = pdfRenderer;
        this.previewCache = previewCache;
    }

    /** What a bill PDF should render with when the shop hasn't saved a design. */
    public record Effective(BillPreset layout, BillLayoutOptions options) {

        static final Effective DEFAULT = new Effective(BillPreset.CLASSIC, BillLayoutOptions.defaults());
    }

    /** Current design + the preset catalog (free read — the designer needs it to gate). */
    @Transactional(readOnly = true)
    public BillLayoutView view(UUID businessId) {
        BillLayout row = layouts.findByBusinessId(businessId).orElse(null);
        Business shop = businesses.findById(businessId).orElse(null);
        return BillLayoutView.of(effective(row).layout(), optionsOf(row),
            shop == null ? null : shop.getLogoUrl());
    }

    @Transactional
    public BillLayoutView upsert(AuthUser authUser, UpsertBillLayoutRequest request) {
        UUID businessId = authUser.businessId();
        BillLayout row = layouts.findByBusinessId(businessId).orElseGet(() -> {
            BillLayout created = new BillLayout();
            created.setBusinessId(businessId);
            return created;
        });
        BillLayoutOptions options = normalise(request.options());
        row.setLayout(request.layout());
        row.setOptions(options);
        layouts.save(row);

        Business shop = businesses.findById(businessId)
            .orElseThrow(() -> ApiException.notFound("Shop not found"));
        applyLogo(shop, request);
        return BillLayoutView.of(request.layout(), options, shop.getLogoUrl());
    }

    /** The design the PDF renderer should use for this shop (defaults when absent). */
    @Transactional(readOnly = true)
    public Effective effectiveLayout(UUID businessId) {
        return effective(layouts.findByBusinessId(businessId).orElse(null));
    }

    /**
     * Renders the synthetic sample bill in the requested design — no DB writes. Cached:
     * the sample is deterministic per params, so repeat requests reuse the same bytes
     * (see {@link BillPreviewCache} for the shared-key invariant). Uncacheable requests
     * (>500-char note) render straight through.
     */
    public byte[] preview(BillPreset layout, boolean showUpiQr, boolean showLogo, String footerNote) {
        String key = BillPreviewCache.keyOf(layout, showUpiQr, showLogo, footerNote);
        if (key != null) {
            byte[] cached = previewCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        byte[] pdf = pdfRenderer.render(
            BillPdfHtml.build(SampleBill.data(layout, showUpiQr, showLogo, footerNote)));
        previewCache.put(key, pdf);
        return pdf;
    }

    private Effective effective(BillLayout row) {
        return row == null ? Effective.DEFAULT : new Effective(row.getLayout(), row.getOptions());
    }

    private static BillLayoutOptions optionsOf(BillLayout row) {
        return row == null ? BillLayoutOptions.defaults() : row.getOptions();
    }

    /** Trim the note so a whitespace-only footer doesn't read as a Pro toggle. */
    private static BillLayoutOptions normalise(BillLayoutOptions options) {
        String note = options.footerNote();
        String trimmed = note == null ? null : note.trim();
        return new BillLayoutOptions(options.showUpiQr(), options.showLogo(),
            trimmed == null || trimmed.isBlank() ? null : trimmed);
    }

    /**
     * Logo replace: when the upload round-trip sends a new pair we store it and delete the
     * replaced Cloudinary image. Omitting both fields leaves the logo untouched; an empty
     * {@code logoUrl} clears it. Old image is deleted after the row is updated so a
     * failure never orphans the live logo (same order as the avatar flow).
     */
    private void applyLogo(Business shop, UpsertBillLayoutRequest request) {
        if (request.logoUrl() == null && request.logoPublicId() == null) {
            return;
        }
        String replaced = shop.getLogoPublicId();
        String url = trimToNull(request.logoUrl());
        String publicId = trimToNull(request.logoPublicId());
        shop.setLogoUrl(url);
        shop.setLogoPublicId(publicId);
        businesses.save(shop);
        if (replaced != null && !replaced.equals(publicId)) {
            media.delete(replaced);
        }
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}