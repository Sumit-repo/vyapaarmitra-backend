package com.vyapaarmitra.api.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.billlayout.BillLayoutService;
import com.vyapaarmitra.api.billlayout.BillPreset;
import org.junit.jupiter.api.Test;

/** The warm-up must never take the boot down with it. */
class PdfWarmUpTest {

    @Test
    void warmUpRendersOneClassicPreview() {
        BillLayoutService service = mock(BillLayoutService.class);

        new PdfWarmUp(service).run(null);

        verify(service).preview(eq(BillPreset.CLASSIC), eq(true), eq(false), isNull());
    }

    @Test
    void aFailingWarmUpNeverThrows() {
        BillLayoutService service = mock(BillLayoutService.class);
        when(service.preview(any(), anyBoolean(), anyBoolean(), any()))
            .thenThrow(new RuntimeException("renderer exploded"));

        assertThatCode(() -> new PdfWarmUp(service).run(null)).doesNotThrowAnyException();
    }
}