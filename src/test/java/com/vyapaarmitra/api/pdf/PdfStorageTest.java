package com.vyapaarmitra.api.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PdfStorageTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadsAsRawPdfAndReturnsSecureUrl() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap()))
            .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/x/raw/upload/pdfs/bill-1.pdf"));

        String url = new PdfStorage(cloudinary).upload(new byte[] {1, 2, 3}, "bill-1");

        assertEquals("https://res.cloudinary.com/x/raw/upload/pdfs/bill-1.pdf", url);
        ArgumentCaptor<Map> opts = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), opts.capture());
        assertEquals("raw", opts.getValue().get("resource_type"));
        assertEquals("bill-1", opts.getValue().get("public_id"));
        assertEquals("pdf", opts.getValue().get("format"));
    }
}
