package com.vyapaarmitra.api.bootstrap;

import com.vyapaarmitra.api.billlayout.BillLayoutService;
import com.vyapaarmitra.api.billlayout.BillPreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * First-boot PDF warm-up: renders one synthetic preview so the first real preview/bill
 * doesn't pay for PdfRenderer's classloading (PDFBox, openhtmltopdf, zxing) and the
 * Devanagari font parse — seconds of one-time cost on a fresh Cloud Run instance.
 * Zero DB, and it pre-seeds the preview cache with the CLASSIC+QR sample. Placed in
 * {@code bootstrap} (not {@code pdf}) because {@code pdf} doesn't know billlayout and
 * a placement there would create a package cycle.
 */
@Slf4j
@Component
public class PdfWarmUp implements ApplicationRunner {

    private final BillLayoutService billLayoutService;

    public PdfWarmUp(BillLayoutService billLayoutService) {
        this.billLayoutService = billLayoutService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long start = System.nanoTime();
            billLayoutService.preview(BillPreset.CLASSIC, true, false, null);
            log.info("PDF warm-up complete in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            // Never throw: a failed warm-up only means the first preview renders cold.
            log.warn("PDF warm-up failed; first preview will render cold", e);
        }
    }
}