package com.vyapaarmitra.api.dataimport;

import com.vyapaarmitra.api.ledger.EntryType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * Parses OkCredit "ACCOUNT STATEMENT" PDFs (Chrome/Skia print output) into the
 * neutral shape the import commit consumes.
 *
 * <p>Layout, verified against real exports: a {@code CUSTOMER|SUPPLIER ACCOUNT
 * STATEMENT} title, a {@code dd MMM yyyy - dd MMM yyyy} range, summary chips
 * ({@code NET BALANCE ₹X DUE | N CREDITS | N PAYMENTS}), then a table whose
 * header is {@code DATE | CUSTOMER/SUPPLIER NAME | NOTES | CREDIT | PAYMENT}.
 * Each transaction row is one line (date, name, note, one amount) followed by
 * 1-2 lines carrying the contact's phone under the name column. The page header
 * also repeats a bare phone number at a completely different x, which must not
 * become a phantom contact.
 *
 * <p>Skia emits one glyph per text-showing operator, so per-chunk (x, y) capture
 * is mandatory — plain text extraction merges whole pages into unusable blobs.
 * Column anchors always come from the statement's OWN header line (never
 * hardcoded x values), so moderate layout shifts stay parseable.
 *
 * <p>Pure Java: no Spring types, so unit tests run it against the sample PDFs
 * directly. Failure is closed — no marker → {@link #CODE_UNSUPPORTED_STATEMENT},
 * marker but zero usable rows → {@link #CODE_PARSE_FAILED}.
 *
 * <p>Still a stateless singleton bean ({@code @Component}) so {@link ImportService}
 * can inject it — annotation is inert in the plain unit tests.
 */
@Component
public class OkCreditStatementParser {

    public static final String CODE_UNSUPPORTED_STATEMENT = "UNSUPPORTED_STATEMENT";
    public static final String CODE_PARSE_FAILED = "PARSE_FAILED";

    /** Vertical tolerance (pt) when grouping glyph chunks into visual lines. */
    private static final float LINE_TOLERANCE = 2.0f;
    /** A phone line attaches to the row above only within this vertical distance (pt). */
    private static final float PHONE_ATTACH_MAX_DELTA = 30.0f;

    private static final Pattern ROW_DATE = Pattern.compile(
        "^\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\.?\\s+\\d{4})\\b");
    private static final Pattern DATE_RANGE = Pattern.compile(
        "^\\s*(?:.*\\|)?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\.?\\s+\\d{4})\\s*[-\\u2013\\u2014]\\s*"
            + "(\\d{1,2}\\s+[A-Za-z]{3,9}\\.?\\s+\\d{4})\\s*$");
    /** Page header of the newer per-contact statement: "Customer: <name> (phone)". */
    private static final Pattern CONTACT_HEADER = Pattern.compile(
        "^\\s*(Customer|Supplier)\\s*:\\s*(.+?)\\s*\\(([+\\d?\\s\\-()]{6,})\\)\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
        // The rupee glyph extracts as U+20B9 on most OkCredit PDFs but degrades to "?"
        // on some CMaps — accept either, or no prefix at all.
        "^[\\u20b9?]\\s*([\\d,]+(?:\\.\\d+)?)$");
    private static final Pattern PHONE_LINE = Pattern.compile("^[+\\d?\\s\\-()]{6,}$");
    /** Summary chips ("N CREDITS" / "N PAYMENTS") — cross-checked against what was parsed. */
    private static final Pattern SUMMARY_COUNT =
        Pattern.compile("\\b(\\d+)\\s+CREDITS?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_PAYMENTS =
        Pattern.compile("\\b(\\d+)\\s+PAYMENTS?\\b", Pattern.CASE_INSENSITIVE);

    /** {@code parse} refuses anything that isn't a recognisable OkCredit statement. */
    public static final class UnparseableStatement extends RuntimeException {

        private final String code;

        UnparseableStatement(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public record ParsedEntry(LocalDate date, EntryType direction, BigDecimal amount, String note) {
    }

    public record ParsedContact(String name, String phone, List<ParsedEntry> entries) {
    }

    /**
     * @param netBalance credits minus payments — what the customer owes (or the payable to a supplier)
     */
    public record ParsedStatement(ImportKind kind, LocalDate dateFrom, LocalDate dateTo,
                                  BigDecimal netBalance, int creditCount, int paymentCount,
                                  List<ParsedContact> contacts, List<String> warnings) {
    }

    private record Chunk(float xCenter, float xStart, float y, String text) {
    }

    /** Glyph chunks sharing a y, ordered left to right. */
    private record Line(float y, List<Chunk> chunks) {

        String text() {
            StringBuilder sb = new StringBuilder();
            for (Chunk chunk : chunks) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(chunk.text().trim());
            }
            return sb.toString();
        }
    }

    /** Column x anchors taken from the statement's own table header line. */
    private record Anchors(Float date, Float name, Float notes, Float credit, Float payment) {

        /** Name column spans from the date/name gap to the name/notes gap (or the credit gap). */
        float nameLower() {
            return date != null && name != null ? midpoint(date, name) : -Float.MAX_VALUE;
        }

        float nameUpper() {
            if (name == null) {
                return Float.MAX_VALUE;
            }
            if (notes != null) {
                return midpoint(name, notes);
            }
            return credit != null ? midpoint(name, credit) : Float.MAX_VALUE;
        }

        float notesLower() {
            if (notes == null) {
                return -Float.MAX_VALUE;
            }
            if (name != null) {
                return midpoint(name, notes);
            }
            // No name column (per-contact statement): notes start after the date column.
            return date != null ? midpoint(date, notes) : -Float.MAX_VALUE;
        }

        float notesUpper() {
            if (notes == null) {
                return Float.MAX_VALUE;
            }
            if (credit != null) {
                return midpoint(notes, credit);
            }
            return payment != null ? midpoint(notes, payment) : Float.MAX_VALUE;
        }

        /** Credits sit left of this x, payments at/after it. */
        float creditPaymentBoundary() {
            return midpoint(credit, payment);
        }

        /** Newer statements flip the two amount columns ("Payment … Credit"); true = old layout. */
        boolean creditIsLeft() {
            return credit != null && payment != null && credit < payment;
        }

        /** Lower bound of whichever amount column sits left of the credit/payment boundary. */
        float amountLeftLower() {
            Float left = creditIsLeft() ? credit : payment;
            if (notes != null && left != null) {
                return midpoint(notes, left);
            }
            return left != null ? left - 40.0f : -Float.MAX_VALUE;
        }
    }

    private static float midpoint(Float a, Float b) {
        if (a == null) {
            return b == null ? Float.MAX_VALUE : b;
        }
        if (b == null) {
            return a;
        }
        return (a + b) / 2f;
    }

    public ParsedStatement parse(byte[] pdf) {
        List<Line> lines = extractLines(pdf);
        ImportKind kind = detectKind(lines);
        if (kind == null) {
            throw new UnparseableStatement(CODE_UNSUPPORTED_STATEMENT,
                "This PDF is not an OkCredit account statement.");
        }

        List<String> warnings = new ArrayList<>();
        LocalDate dateFrom = null;
        LocalDate dateTo = null;

        Anchors anchors = null;
        Row current = null;
        Map<String, Row> rows = new LinkedHashMap<>();
        boolean sawHeader = false;
        String headerName = null;
        String headerPhone = null;

        for (Line line : lines) {
            // The statement's own header declares the columns; re-detected on each page.
            Anchors headerAnchors = tableHeaderAnchors(line);
            if (headerAnchors != null) {
                anchors = headerAnchors;
                sawHeader = true;
                current = null;
                continue;
            }

            // The statement range sits between the title and the table header.
            if (dateFrom == null && !sawHeader) {
                Matcher range = DATE_RANGE.matcher(line.text());
                if (range.matches()) {
                    dateFrom = parseDate(range.group(1));
                    dateTo = parseDate(range.group(2));
                    continue;
                }
            }

            // Newer statements name the contact in the page header, not the table.
            if (headerName == null) {
                Matcher contact = CONTACT_HEADER.matcher(line.text());
                if (contact.matches()) {
                    headerName = contact.group(2).trim();
                    String digits = contact.group(3).replaceAll("[^+\\d]", "");
                    headerPhone = digits.length() >= 6 ? digits : null;
                    continue;
                }
            }

            if (anchors == null) {
                continue; // nothing above the table header can be a transaction
            }

            Matcher date = ROW_DATE.matcher(line.text());
            if (date.find()) {
                current = readRow(line, date.group(1), anchors, headerName, headerPhone, rows, warnings);
                continue;
            }

            // Continuation line: the phone printed under the contact name.
            if (current != null && current.phone == null
                && line.y() - current.lineY <= PHONE_ATTACH_MAX_DELTA) {
                String phone = phoneOnLine(line, anchors);
                if (phone != null) {
                    current.phone = phone;
                }
            }
        }

        if (rows.isEmpty()) {
            throw new UnparseableStatement(CODE_PARSE_FAILED,
                "No transactions could be read from this statement.");
        }

        List<ParsedContact> contacts = groupContacts(rows.values());
        if (dateFrom == null) {
            List<LocalDate> dates = contacts.stream().flatMap(c -> c.entries().stream())
                .map(ParsedEntry::date).sorted().toList();
            dateFrom = dates.get(0);
            dateTo = dates.get(dates.size() - 1);
            warnings.add("Statement date range was not readable; derived from transaction dates.");
        }

        BigDecimal creditTotal = BigDecimal.ZERO;
        BigDecimal paymentTotal = BigDecimal.ZERO;
        int creditCount = 0;
        int paymentCount = 0;
        for (ParsedContact contact : contacts) {
            for (ParsedEntry entry : contact.entries()) {
                if (entry.direction() == EntryType.CREDIT) {
                    creditTotal = creditTotal.add(entry.amount());
                    creditCount++;
                } else {
                    paymentTotal = paymentTotal.add(entry.amount());
                    paymentCount++;
                }
            }
        }

        checkAgainstSummary(lines, creditCount, paymentCount, warnings);

        return new ParsedStatement(kind, dateFrom, dateTo, creditTotal.subtract(paymentTotal),
            creditCount, paymentCount, contacts, warnings);
    }

    // ------------------------------------------------------------------ extraction

    private List<Line> extractLines(byte[] pdf) {
        List<Chunk> chunks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            PositionStripper stripper = new PositionStripper(chunks);
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            stripper.getText(document);
        } catch (IOException | IllegalArgumentException | NegativeArraySizeException e) {
            throw new UnparseableStatement(CODE_PARSE_FAILED, "Could not read the statement PDF.");
        }

        chunks.sort(Comparator.comparingDouble(Chunk::y).thenComparingDouble(Chunk::xStart));
        List<Line> lines = new ArrayList<>();
        float lineY = Float.NaN;
        List<Chunk> current = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (Float.isNaN(lineY) || Math.abs(chunk.y() - lineY) <= LINE_TOLERANCE) {
                lineY = Float.isNaN(lineY) ? chunk.y() : lineY;
                current.add(chunk);
            } else {
                lines.add(new Line(lineY, List.copyOf(current)));
                current = new ArrayList<>();
                current.add(chunk);
                lineY = chunk.y();
            }
        }
        if (!current.isEmpty()) {
            lines.add(new Line(lineY, List.copyOf(current)));
        }
        return lines;
    }

    /** Captures where every text run sits — Skia prints one glyph per Tj, so x/y is the layout. */
    private static final class PositionStripper extends PDFTextStripper {

        private final List<Chunk> chunks;

        PositionStripper(List<Chunk> chunks) throws IOException {
            super();
            this.chunks = chunks;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions == null || positions.isEmpty()) {
                return;
            }
            TextPosition first = positions.get(0);
            TextPosition last = positions.get(positions.size() - 1);
            float xStart = first.getX();
            float xEnd = last.getX() + last.getWidth();
            chunks.add(new Chunk((xStart + xEnd) / 2f, xStart, first.getY(), text));
        }
    }

    // -------------------------------------------------------------------- detection

    private ImportKind detectKind(List<Line> lines) {
        for (Line line : lines) {
            String squashed = line.text().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            if (squashed.contains("CUSTOMERACCOUNTSTATEMENT")) {
                return ImportKind.CUSTOMER;
            }
            if (squashed.contains("SUPPLIERACCOUNTSTATEMENT")) {
                return ImportKind.SUPPLIER;
            }
            // Newer OkCredit builds dropped the "… Account Statement" title line; the
            // per-contact header ("Customer: <name> (phone)") is now the only kind marker.
            if (squashed.startsWith("CUSTOMER:")) {
                return ImportKind.CUSTOMER;
            }
            if (squashed.startsWith("SUPPLIER:")) {
                return ImportKind.SUPPLIER;
            }
        }
        return null;
    }

    /** Header line = contains DATE + CREDIT + PAYMENT words; returns the x anchors it declares. */
    private Anchors tableHeaderAnchors(Line line) {
        boolean hasDate = false;
        boolean hasCredit = false;
        boolean hasPayment = false;
        Float date = null;
        Float name = null;
        Float notes = null;
        Float credit = null;
        Float payment = null;
        for (Chunk chunk : line.chunks()) {
            // Newer statements print the running count in the header ("Payment(1)").
            String label = chunk.text().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ")
                .replaceFirst("\\(\\d+\\)$", "").trim();
            switch (label) {
                case "DATE" -> {
                    hasDate = true;
                    date = chunk.xStart();
                }
                case "CUSTOMER NAME", "SUPPLIER NAME", "PARTY NAME" -> name =
                    name == null ? chunk.xStart() : name;
                case "NOTES", "NOTE" -> notes = notes == null ? chunk.xStart() : notes;
                case "CREDIT" -> {
                    hasCredit = true;
                    credit = chunk.xStart();
                }
                case "PAYMENT" -> {
                    hasPayment = true;
                    payment = chunk.xStart();
                }
                default -> {
                }
            }
        }
        if (!hasDate || !hasCredit || !hasPayment || credit == null || payment == null) {
            return null;
        }
        return new Anchors(date, name, notes, credit, payment);
    }

    // ------------------------------------------------------------------------- rows

    private static final class Row {

        final LocalDate date;
        final String name;
        String phone;
        BigDecimal credit;
        BigDecimal payment;
        String note;
        final float lineY;

        Row(LocalDate date, String name, float lineY) {
            this.date = date;
            this.name = name;
            this.lineY = lineY;
        }
    }

    private Row readRow(Line line, String dateText, Anchors anchors, String headerName,
                        String headerPhone, Map<String, Row> rows, List<String> warnings) {
        LocalDate date = parseDate(dateText);
        if (date == null) {
            return current(rows); // unreadable date: keep the previous row for phone lines
        }

        String name;
        String phone = null;
        if (anchors.name() == null) {
            // Newer per-contact statement: the table has no name column — the page header
            // ("Customer: <name> (phone)") is the only place the contact is named.
            if (headerName == null) {
                warnings.add("Transaction on " + dateText + " had no readable contact name and was skipped.");
                return current(rows);
            }
            name = headerName;
            phone = headerPhone;
        } else {
            name = textBetween(line, anchors.nameLower(), anchors.nameUpper());
            if (name == null) {
                warnings.add("Transaction on " + dateText + " had no readable contact name and was skipped.");
                return current(rows);
            }
        }

        Row row = new Row(date, name, line.y());
        row.phone = phone;
        row.note = textBetween(line, anchors.notesLower(), anchors.notesUpper());
        boolean creditLeft = anchors.creditIsLeft();
        row.credit = creditLeft
            ? amountInColumn(line, anchors.amountLeftLower(), anchors.creditPaymentBoundary())
            : amountInColumn(line, anchors.creditPaymentBoundary(), Float.MAX_VALUE);
        row.payment = creditLeft
            ? amountInColumn(line, anchors.creditPaymentBoundary(), Float.MAX_VALUE)
            : amountInColumn(line, anchors.amountLeftLower(), anchors.creditPaymentBoundary());

        if (row.credit == null && row.payment == null) {
            warnings.add("Transaction on " + dateText + " for " + name + " had no amount and was skipped.");
            return row;
        }
        rows.put(rows.size() + "|" + name.toLowerCase(Locale.ROOT), row);
        return row;
    }

    /** Rows keyed by document order; the "current" row for phone attachment when a row is skipped. */
    private Row current(Map<String, Row> rows) {
        Row last = null;
        for (Row row : rows.values()) {
            last = row;
        }
        return last;
    }

    /** First amount token whose centre falls inside [lower, upper); null when the column is empty. */
    private BigDecimal amountInColumn(Line line, float lower, float upper) {
        for (Chunk chunk : line.chunks()) {
            if (chunk.xCenter() < lower || chunk.xCenter() >= upper) {
                continue;
            }
            Matcher amount = AMOUNT.matcher(chunk.text().trim());
            if (amount.matches()) {
                return new BigDecimal(amount.group(1).replace(",", ""));
            }
        }
        return null;
    }

    /** Free-text chunks inside [lower, upper), joined — notes stay verbatim. */
    private String textBetween(Line line, float lower, float upper) {
        StringBuilder sb = new StringBuilder();
        for (Chunk chunk : line.chunks()) {
            if (chunk.xCenter() < lower || chunk.xCenter() >= upper) {
                continue;
            }
            String text = chunk.text().trim();
            if (text.isEmpty() || AMOUNT.matcher(text).matches()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(text);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Phone digits from a line that carries nothing else. Bare digits only — the row's
     * date line and free-text lines can never be mistaken for one.
     */
    private String phoneOnLine(Line line, Anchors anchors) {
        String text = line.text().trim();
        if (text.isEmpty() || !PHONE_LINE.matcher(text).matches()) {
            return null;
        }
        String digits = text.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 13) {
            return null;
        }
        // Must sit in the name column, or it's the page header/footer phone.
        for (Chunk chunk : line.chunks()) {
            if (chunk.xCenter() >= anchors.nameLower() && chunk.xCenter() < anchors.nameUpper()
                && !AMOUNT.matcher(chunk.text().trim()).matches()) {
                return digits;
            }
        }
        return null;
    }

    /** Group rows into contacts by trimmed, case-insensitive name, in document order. */
    private List<ParsedContact> groupContacts(Iterable<Row> rows) {
        Map<String, ParsedContact> contacts = new LinkedHashMap<>();
        for (Row row : rows) {
            String key = row.name.trim().toLowerCase(Locale.ROOT);
            ParsedContact contact = contacts.get(key);
            if (contact == null) {
                contact = new ParsedContact(row.name.trim(), row.phone, new ArrayList<>());
                contacts.put(key, contact);
            }
            if (contact.phone() == null && row.phone != null) {
                // Records are immutable, so rebuild with the phone once it shows up.
                contacts.put(key, new ParsedContact(contact.name(), row.phone, contact.entries()));
            }
            if (row.credit != null) {
                contact.entries().add(new ParsedEntry(row.date, EntryType.CREDIT, row.credit, row.note));
            }
            if (row.payment != null) {
                contact.entries().add(new ParsedEntry(row.date, EntryType.PAYMENT, row.payment, row.note));
            }
        }
        return new ArrayList<>(contacts.values());
    }

    /**
     * Cross-checks the parsed counts against the statement's own summary chips — the
     * cheapest guard against silently dropped pages/rows.
     */
    private void checkAgainstSummary(List<Line> lines, int creditCount, int paymentCount,
                                     List<String> warnings) {
        Integer expectedCredits = null;
        Integer expectedPayments = null;
        for (Line line : lines) {
            Matcher credits = SUMMARY_COUNT.matcher(line.text());
            if (credits.find()) {
                expectedCredits = Integer.valueOf(credits.group(1));
            }
            Matcher payments = SUMMARY_PAYMENTS.matcher(line.text());
            if (payments.find()) {
                expectedPayments = Integer.valueOf(payments.group(1));
            }
        }
        if (expectedCredits != null && expectedCredits != creditCount) {
            warnings.add("Statement reports " + expectedCredits + " credit transactions but "
                + creditCount + " were read.");
        }
        if (expectedPayments != null && expectedPayments != paymentCount) {
            warnings.add("Statement reports " + expectedPayments + " payment transactions but "
                + paymentCount + " were read.");
        }
    }

    // ------------------------------------------------------------------- date utils

    /**
     * {@code 09 Aug 2026} / {@code 03 Sept 2026} — OkCredit abbreviates September as
     * "Sept", so the month is matched on its first three letters.
     */
    private LocalDate parseDate(String text) {
        Matcher matcher = Pattern.compile(
            "^(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})$").matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        int day = Integer.parseInt(matcher.group(1));
        int month = monthOf(matcher.group(2));
        if (month <= 0 || day < 1 || day > 31) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(matcher.group(3)), month, day);
    }

    private static int monthOf(String token) {
        String key = token.trim().substring(0, 3).toUpperCase(Locale.ROOT);
        return switch (key) {
            case "JAN" -> 1;
            case "FEB" -> 2;
            case "MAR" -> 3;
            case "APR" -> 4;
            case "MAY" -> 5;
            case "JUN" -> 6;
            case "JUL" -> 7;
            case "AUG" -> 8;
            case "SEP" -> 9;
            case "OCT" -> 10;
            case "NOV" -> 11;
            case "DEC" -> 12;
            default -> 0;
        };
    }
}