package com.vyapaarmitra.api.billlayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.BillLayoutView;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.UpsertBillLayoutRequest;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.media.MediaService;
import com.vyapaarmitra.api.pdf.PdfRenderer;
import com.vyapaarmitra.api.user.Role;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Bill design persistence: absent row ⇒ CLASSIC, logo replace deletes the old image. */
@ExtendWith(MockitoExtension.class)
class BillLayoutServiceTest {

    @Mock private BillLayoutRepository layouts;
    @Mock private BusinessRepository businesses;
    @Mock private MediaService media;
    @Mock private PdfRenderer pdfRenderer;

    private BillLayoutService service;

    @BeforeEach
    void setUp() {
        // Real cache collaborator — a mocked one couldn't demonstrate the cached path.
        service = new BillLayoutService(layouts, businesses, media, pdfRenderer,
            new BillPreviewCache());
    }

    private final UUID businessId = UUID.randomUUID();
    private final AuthUser owner = new AuthUser(UUID.randomUUID(), businessId, Role.OWNER);

    private static Business shop(String logoUrl, String logoPublicId) {
        Business b = new Business();
        b.setId(UUID.randomUUID());
        b.setName("Sharma Kirana Store");
        b.setLogoUrl(logoUrl);
        b.setLogoPublicId(logoPublicId);
        return b;
    }

    @Test
    void viewFallsBackToClassicWhenNothingWasSaved() {
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop(null, null)));

        BillLayoutView view = service.view(businessId);

        assertThat(view.layout()).isEqualTo("CLASSIC");
        assertThat(view.options().showUpiQr()).isFalse();
        assertThat(view.options().showLogo()).isFalse();
        assertThat(view.options().footerNote()).isNull();
        assertThat(view.logoUrl()).isNull();
        assertThat(view.available()).hasSize(3);
        assertThat(view.available().get(0)).isEqualTo(
            new BillLayoutDtos.PresetView("CLASSIC", "Classic", false));
        assertThat(view.available().get(1)).isEqualTo(
            new BillLayoutDtos.PresetView("MINIMAL", "Minimal", true));
        assertThat(view.available().get(2)).isEqualTo(
            new BillLayoutDtos.PresetView("BOLD", "Bold", true));
    }

    @Test
    void viewReturnsTheSavedDesign() {
        BillLayout row = new BillLayout();
        row.setBusinessId(businessId);
        row.setLayout(BillPreset.MINIMAL);
        row.setOptions(new BillLayoutOptions(true, true, "GST extra on request"));
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.of(row));
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop("https://logo", "logo-id")));

        BillLayoutView view = service.view(businessId);

        assertThat(view.layout()).isEqualTo("MINIMAL");
        assertThat(view.options().showUpiQr()).isTrue();
        assertThat(view.options().showLogo()).isTrue();
        assertThat(view.options().footerNote()).isEqualTo("GST extra on request");
        assertThat(view.logoUrl()).isEqualTo("https://logo");
    }

    @Test
    void effectiveLayoutDefaultsToClassic() {
        assertThat(service.effectiveLayout(businessId))
            .isEqualTo(new BillLayoutService.Effective(BillPreset.CLASSIC, BillLayoutOptions.defaults()));
    }

    @Test
    void upsertSavesTheDesign() {
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop(null, null)));
        UpsertBillLayoutRequest request = new UpsertBillLayoutRequest(BillPreset.BOLD,
            new BillLayoutOptions(true, false, "Thanks!"), null, null);

        BillLayoutView view = service.upsert(owner, request);

        verify(layouts).save(any(BillLayout.class));
        assertThat(view.layout()).isEqualTo("BOLD");
        assertThat(view.options().showUpiQr()).isTrue();
        assertThat(view.logoUrl()).isNull();
        verify(media, never()).delete(any());
    }

    @Test
    void upsertReplacesTheLogoAndDeletesTheOldUpload() {
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.empty());
        Business shop = shop("https://old", "old-id");
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop));
        UpsertBillLayoutRequest request = new UpsertBillLayoutRequest(BillPreset.CLASSIC,
            BillLayoutOptions.defaults(), "https://new", "new-id");

        BillLayoutView view = service.upsert(owner, request);

        assertThat(shop.getLogoUrl()).isEqualTo("https://new");
        assertThat(shop.getLogoPublicId()).isEqualTo("new-id");
        assertThat(view.logoUrl()).isEqualTo("https://new");
        verify(businesses).save(shop);
        verify(media).delete("old-id");
    }

    @Test
    void upsertWithoutLogoFieldsLeavesTheLogoAlone() {
        BillLayout row = new BillLayout();
        row.setBusinessId(businessId);
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.of(row));
        Business shop = shop("https://logo", "logo-id");
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop));
        UpsertBillLayoutRequest request = new UpsertBillLayoutRequest(BillPreset.CLASSIC,
            BillLayoutOptions.defaults(), null, null);

        BillLayoutView view = service.upsert(owner, request);

        assertThat(shop.getLogoUrl()).isEqualTo("https://logo");
        assertThat(shop.getLogoPublicId()).isEqualTo("logo-id");
        assertThat(view.logoUrl()).isEqualTo("https://logo");
        verify(media, never()).delete(any());
    }

    @Test
    void upsertWithAnEmptyLogoUrlClearsIt() {
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.empty());
        Business shop = shop("https://old", "old-id");
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop));
        UpsertBillLayoutRequest request = new UpsertBillLayoutRequest(BillPreset.CLASSIC,
            BillLayoutOptions.defaults(), "", null);

        BillLayoutView view = service.upsert(owner, request);

        assertThat(shop.getLogoUrl()).isNull();
        assertThat(shop.getLogoPublicId()).isNull();
        assertThat(view.logoUrl()).isNull();
        verify(media).delete("old-id");
    }

    @Test
    void upsertTrimsAWhitespaceOnlyFooterNote() {
        when(layouts.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(businesses.findById(businessId)).thenReturn(Optional.of(shop(null, null)));
        UpsertBillLayoutRequest request = new UpsertBillLayoutRequest(BillPreset.CLASSIC,
            new BillLayoutOptions(false, false, "   "), null, null);

        assertThat(service.upsert(owner, request).options().footerNote()).isNull();
    }

    @Test
    void previewRendersTheSampleBillToPdfBytes() {
        // The service's own render path, with a real renderer (mocks can't produce bytes).
        BillLayoutService real = new BillLayoutService(layouts, businesses, media,
            new PdfRenderer(), new BillPreviewCache());
        assertThat(new String(real.preview(BillPreset.MINIMAL, true, true, "Thanks"),
            0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void previewCachesRepeatRequestsForTheSameParams() {
        CountingRenderer renderer = new CountingRenderer();
        BillLayoutService real = new BillLayoutService(layouts, businesses, media, renderer,
            new BillPreviewCache());

        byte[] first = real.preview(BillPreset.MINIMAL, true, true, "Thanks");
        byte[] second = real.preview(BillPreset.MINIMAL, true, true, "Thanks");

        assertThat(second).isSameAs(first);
        assertThat(renderer.rendered).isEqualTo(1);
    }

    @Test
    void previewRendersAgainWhenParamsChange() {
        CountingRenderer renderer = new CountingRenderer();
        BillLayoutService real = new BillLayoutService(layouts, businesses, media, renderer,
            new BillPreviewCache());

        real.preview(BillPreset.MINIMAL, true, true, "Thanks");
        real.preview(BillPreset.MINIMAL, true, false, "Thanks");

        assertThat(renderer.rendered).isEqualTo(2);
    }

    /** Counts renders; a mock can't produce the %PDF- bytes the cached-path tests assert on. */
    private static final class CountingRenderer extends PdfRenderer {
        private int rendered;

        @Override
        public byte[] render(String xhtml) {
            rendered++;
            return super.render(xhtml);
        }
    }
}