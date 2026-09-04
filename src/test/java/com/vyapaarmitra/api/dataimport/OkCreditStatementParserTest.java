package com.vyapaarmitra.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedContact;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.ParsedStatement;
import com.vyapaarmitra.api.dataimport.OkCreditStatementParser.UnparseableStatement;
import com.vyapaarmitra.api.ledger.EntryType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

/**
 * The parser is exercised against the real OkCredit samples shipped in test
 * resources — these are the exact files merchants will upload.
 */
class OkCreditStatementParserTest {

    private final OkCreditStatementParser parser = new OkCreditStatementParser();

    private static final String CUSTOMER_SAMPLE = "OkCredit_CustomerAccountStatement_03 Sept 1926_03 Sept 2026.pdf";
    private static final String SUPPLIER_SAMPLE = "OkCredit_SupplierAccountStatement_03 Sept 1926_03 Sept 2026.pdf";

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = OkCreditStatementParserTest.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + name);
            }
            return in.readAllBytes();
        }
    }

    @Test
    void customerStatementParsesSingleContactWithBothEntries() throws IOException {
        ParsedStatement statement = parser.parse(resource(CUSTOMER_SAMPLE));

        assertThat(statement.kind()).isEqualTo(ImportKind.CUSTOMER);
        assertThat(statement.dateFrom()).isEqualTo(LocalDate.of(1926, 9, 3));
        assertThat(statement.dateTo()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(statement.creditCount()).isEqualTo(1);
        assertThat(statement.paymentCount()).isEqualTo(1);
        assertThat(statement.netBalance()).isEqualByComparingTo("1");

        assertThat(statement.contacts()).hasSize(1);
        ParsedContact contact = statement.contacts().get(0);
        assertThat(contact.name()).isEqualTo("Demo Customer");
        // The page header repeats the same digits at a different x — it must not create
        // a phantom contact, and the phone must land on the row it belongs to.
        assertThat(contact.phone()).isEqualTo("9122244192");
        assertThat(contact.entries()).hasSize(2);
        assertThat(contact.entries().get(0))
            .satisfies(entry -> {
                assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 9));
                assertThat(entry.direction()).isEqualTo(EntryType.PAYMENT);
                assertThat(entry.amount()).isEqualByComparingTo("1");
                assertThat(entry.note()).isEqualTo("Cash paid");
            });
        assertThat(contact.entries().get(1))
            .satisfies(entry -> {
                assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 9));
                assertThat(entry.direction()).isEqualTo(EntryType.CREDIT);
                assertThat(entry.amount()).isEqualByComparingTo("2");
                assertThat(entry.note()).isEqualTo("Items on credit");
            });
    }

    @Test
    void supplierStatementGroupsEverySupplier() throws IOException {
        ParsedStatement statement = parser.parse(resource(SUPPLIER_SAMPLE));

        assertThat(statement.kind()).isEqualTo(ImportKind.SUPPLIER);
        assertThat(statement.dateFrom()).isEqualTo(LocalDate.of(1926, 9, 3));
        assertThat(statement.dateTo()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(statement.creditCount()).isEqualTo(5);
        assertThat(statement.paymentCount()).isEqualTo(4);
        assertThat(statement.netBalance()).isEqualByComparingTo("2");

        assertThat(statement.contacts()).hasSize(3);
        assertThat(statement.contacts().get(0).name()).isEqualTo("uma xerox");
        assertThat(statement.contacts().get(0).phone()).isEqualTo("7004211630");
        assertThat(statement.contacts().get(0).entries()).hasSize(5);
        assertThat(statement.contacts().get(1).name()).isEqualTo("Demo Supplier");
        assertThat(statement.contacts().get(1).phone()).isEqualTo("9122244192");
        assertThat(statement.contacts().get(1).entries()).hasSize(2);
        assertThat(statement.contacts().get(2).name()).isEqualTo("Rajshree Enterprise");
        assertThat(statement.contacts().get(2).phone()).isEqualTo("7979849880");
        assertThat(statement.contacts().get(2).entries()).hasSize(2);

        // Per-contact totals — the aggregate rows in OkCredit statements are not totals.
        assertThat(creditTotal(statement.contacts().get(0))).isEqualByComparingTo("3");
        assertThat(paymentTotal(statement.contacts().get(0))).isEqualByComparingTo("2");
        assertThat(creditTotal(statement.contacts().get(1))).isEqualByComparingTo("2");
        assertThat(paymentTotal(statement.contacts().get(1))).isEqualByComparingTo("1");
        assertThat(creditTotal(statement.contacts().get(2))).isEqualByComparingTo("282");
        assertThat(paymentTotal(statement.contacts().get(2))).isEqualByComparingTo("282");

        // Notes stay verbatim, and amountless notes read as null rather than "".
        assertThat(statement.contacts().get(1).entries().get(0).note()).isEqualTo("Cash paid");
        assertThat(statement.contacts().get(1).entries().get(1).note()).isEqualTo("Items on credit");
        assertThat(statement.contacts().get(0).entries().get(0).note()).isNull();

        // The old Rajshree entries keep their real (pre-statement) dates.
        assertThat(statement.contacts().get(2).entries().get(0).date()).isEqualTo(LocalDate.of(2021, 10, 29));
        assertThat(statement.contacts().get(2).entries().get(0).direction()).isEqualTo(EntryType.PAYMENT);
        assertThat(statement.contacts().get(2).entries().get(1).date()).isEqualTo(LocalDate.of(2019, 5, 25));
    }

    private static BigDecimal creditTotal(ParsedContact contact) {
        return contact.entries().stream()
            .filter(entry -> entry.direction() == EntryType.CREDIT)
            .map(entry -> entry.amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal paymentTotal(ParsedContact contact) {
        return contact.entries().stream()
            .filter(entry -> entry.direction() == EntryType.PAYMENT)
            .map(entry -> entry.amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void pdfWithoutStatementMarkerIsUnsupported() throws IOException {
        byte[] pdf = emptyPdf();
        assertThatThrownBy(() -> parser.parse(pdf))
            .isInstanceOf(UnparseableStatement.class)
            .satisfies(e -> assertThat(((UnparseableStatement) e).getCode())
                .isEqualTo(OkCreditStatementParser.CODE_UNSUPPORTED_STATEMENT));
    }

    @Test
    void corruptPdfFailsClosed() {
        assertThatThrownBy(() -> parser.parse("this is not a pdf at all".getBytes()))
            .isInstanceOf(UnparseableStatement.class)
            .satisfies(e -> assertThat(((UnparseableStatement) e).getCode())
                .isEqualTo(OkCreditStatementParser.CODE_PARSE_FAILED));
    }

    @Test
    void markerWithoutTransactionsFailsClosed() throws IOException {
        byte[] pdf = titledPdf("CUSTOMER ACCOUNT STATEMENT");
        assertThatThrownBy(() -> parser.parse(pdf))
            .isInstanceOf(UnparseableStatement.class)
            .satisfies(e -> assertThat(((UnparseableStatement) e).getCode())
                .isEqualTo(OkCreditStatementParser.CODE_PARSE_FAILED));
    }

    // ------------------------------------------------------------------ pdf builders

    /** A valid PDF with no text at all — everything but a statement. */
    private static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return toBytes(document);
        }
    }

    /** A valid PDF carrying a statement title but no table rows. */
    private static byte[] titledPdf(String title) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText(title);
                content.endText();
            }
            return toBytes(document);
        }
    }

    private static byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}