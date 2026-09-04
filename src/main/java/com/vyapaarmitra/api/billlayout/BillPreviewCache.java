package com.vyapaarmitra.api.billlayout;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-process cache for the synthetic sample-bill preview. {@code SampleBill} is fully
 * deterministic per query string — the same bytes every time — so the preview is pure
 * CPU we can memoise instead of re-rendering PDFBox output on every designer toggle.
 *
 * <p>INVARIANT: the cache key deliberately omits {@code businessId} because
 * {@code SampleBill} is synthetic (fixed shop name, fixed ids, a drawn placeholder
 * logo) — the cached bytes are identical for every shop and leak nothing. If
 * {@code SampleBill} ever becomes business-aware (real logo, real VPA), the key MUST
 * grow {@code businessId} or this cache becomes a cross-shop data leak.
 *
 * <p>Contract: callers render OUTSIDE the cache — {@code get} → miss → render →
 * {@code put}. Never render inside {@code computeIfAbsent}; two concurrent renders of
 * the same key are idempotent and the later put simply wins. {@code keyOf} returns
 * {@code null} for an uncacheable request (>500-char footer note — the preview has no
 * {@code @Size} on the note, so this both bounds keys and skips aliasing), and callers
 * render uncached then.
 */
@Component
public class BillPreviewCache {

    private static final int MAX_NOTE_CHARS = 500;
    private static final int MAX_PDF_BYTES = 2 * 1024 * 1024;

    private final Duration ttl;
    private final int maxEntries;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(byte[] pdf, Instant expiresAt) {
    }

    public BillPreviewCache() {
        this(Duration.ofMinutes(30), 64);
    }

    /** Test-visible tuning: ttl=0 expires immediately; small maxEntries forces eviction. */
    BillPreviewCache(Duration ttl, int maxEntries) {
        this.ttl = ttl;
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** Cache key for a preview request, or {@code null} when the request is uncacheable. */
    static String keyOf(BillPreset layout, boolean showUpiQr, boolean showLogo, String footerNote) {
        String note = footerNote == null ? "" : footerNote.trim();
        if (note.length() > MAX_NOTE_CHARS) {
            return null;
        }
        return layout.name() + "|" + showUpiQr + "|" + showLogo + "|" + note;
    }

    /** Cached bytes for the key, or {@code null} on miss/expiry (expired entries removed). */
    public byte[] get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        // Valid strictly before the deadline — a ttl of ZERO is expired even on the same tick.
        if (!Instant.now().isBefore(entry.expiresAt())) {
            entries.remove(key, entry);
            return null;
        }
        return entry.pdf();
    }

    /** Stores a rendered preview; oversize PDFs and unbounded growth are ignored. */
    public void put(String key, byte[] pdf) {
        if (key == null || pdf == null || pdf.length > MAX_PDF_BYTES) {
            return;
        }
        if (entries.size() >= maxEntries) {
            Instant now = Instant.now();
            entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
            // Crude backstop if the sweep freed nothing (all entries fresh).
            if (entries.size() >= maxEntries) {
                entries.clear();
            }
        }
        entries.put(key, new Entry(pdf, Instant.now().plus(ttl)));
    }
}