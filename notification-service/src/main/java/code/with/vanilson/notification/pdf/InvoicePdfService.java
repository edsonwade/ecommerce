package code.with.vanilson.notification.pdf;

import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * InvoicePdfService — Infrastructure Layer (Document Generation)
 * <p>
 * Generates professional A4 PDF invoices using OpenPDF (open-source iText fork).
 * Returns raw bytes — caller attaches them to email without touching the filesystem.
 * <p>
 * Single Responsibility (SOLID-S): only PDF generation.
 * Stateless + thread-safe — no instance fields beyond injected abstractions.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class InvoicePdfService {

    private static final Color BRAND_BLUE  = new Color(0, 82, 155);
    private static final Color LIGHT_GREY  = new Color(245, 245, 245);
    private static final Color TEXT_DARK   = new Color(33, 33, 33);
    private static final Color BORDER_GREY = new Color(220, 220, 220);
    private static final Color TOTAL_BG    = new Color(230, 240, 255);
    private static final float TAX_RATE    = 0.23f;

    private final MessageSource messageSource;

    public InvoicePdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public byte[] generateInvoice(OrderConfirmation order) {
        log.info(msg("notification.log.pdf.generating", order.orderReference()));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            renderHeader(doc, order);
            renderCustomer(doc, order);
            renderLineItems(doc, order.products());
            renderTotals(doc, order.totalAmount());
            renderFooter(doc, order);
            doc.close();
            log.info(msg("notification.log.pdf.generated", order.orderReference(), out.size()));
            return out.toByteArray();
        } catch (Exception ex) {
            log.error(msg("notification.log.pdf.failed", order.orderReference(), ex.getMessage()), ex);
            throw new RuntimeException("PDF invoice generation failed: " + order.orderReference(), ex);
        }
    }

    // -------------------------------------------------------
    private void renderHeader(Document doc, OrderConfirmation order) throws Exception {
        Font co = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND_BLUE);
        Font tg = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font ti = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TEXT_DARK);
        Font dt = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK);

        left(doc, "VanilsonShop", co);
        left(doc, "SaaS eCommerce Platform", tg);
        doc.add(spacer());
        right(doc, "INVOICE", ti);
        right(doc, "Invoice #: INV-" + order.orderReference(), dt);
        right(doc, "Date: " + now(), dt);
        doc.add(spacer());
    }

    private void renderCustomer(Document doc, OrderConfirmation order) throws Exception {
        Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_BLUE);
        Font value = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK);
        doc.add(new Paragraph("Bill To:", label));
        OrderConfirmation.CustomerSummary c = order.customer();
        doc.add(new Paragraph(c.firstname() + " " + c.lastname(), value));
        doc.add(new Paragraph("Email: " + c.email(), value));
        doc.add(new Paragraph("Order Ref: " + order.orderReference(), value));
        doc.add(spacer());
    }

    private void renderLineItems(Document doc,
                                  List<OrderConfirmation.ProductSummary> products) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{4f, 1.5f, 2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8f);
        for (String h : new String[]{"Product", "Qty", "Unit Price", "Total"}) headerCell(table, h);

        NumberFormat fmt = currencyFmt();
        boolean alt = false;
        for (OrderConfirmation.ProductSummary p : products) {
            Color bg = alt ? LIGHT_GREY : Color.WHITE;
            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(p.quantity()));
            dataCell(table, p.name(),             bg, Element.ALIGN_LEFT);
            dataCell(table, fmtQty(p.quantity()), bg, Element.ALIGN_CENTER);
            dataCell(table, fmt.format(p.price()), bg, Element.ALIGN_RIGHT);
            dataCell(table, fmt.format(lineTotal), bg, Element.ALIGN_RIGHT);
            alt = !alt;
        }
        doc.add(table);
        doc.add(spacer());
    }

    private void renderTotals(Document doc, BigDecimal subtotal) throws Exception {
        NumberFormat fmt = currencyFmt();
        BigDecimal tax   = subtotal.multiply(BigDecimal.valueOf(TAX_RATE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);
        Font norm = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK);
        Font big  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_BLUE);

        PdfPTable t = new PdfPTable(new float[]{3f, 2f});
        t.setWidthPercentage(38);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalRow(t, "Subtotal (excl. IVA):", fmt.format(subtotal), bold, norm, Color.WHITE);
        totalRow(t, "IVA (23%):",             fmt.format(tax),     bold, norm, Color.WHITE);
        totalRow(t, "TOTAL:",                  fmt.format(total),  big,  big,  TOTAL_BG);
        doc.add(t);
        doc.add(spacer());
    }

    private void renderFooter(Document doc, OrderConfirmation order) throws Exception {
        Font grey  = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font thank = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BLUE);
        doc.add(new Paragraph("Payment Method: " + order.paymentMethod(), grey));
        doc.add(spacer());
        Paragraph ty = new Paragraph("Thank you for your order!", thank);
        ty.setAlignment(Element.ALIGN_CENTER);
        doc.add(ty);
        Paragraph sup = new Paragraph("Questions? support@vanilsonshop.io", grey);
        sup.setAlignment(Element.ALIGN_CENTER);
        doc.add(sup);
    }

    // -------------------------------------------------------
    private void headerCell(PdfPTable t, String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(BRAND_BLUE);
        c.setPadding(8f);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private void dataCell(PdfPTable t, String text, Color bg, int align) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setPadding(6f);
        c.setBorderColor(BORDER_GREY);
        c.setBorder(Rectangle.BOTTOM);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private void totalRow(PdfPTable t, String label, String value,
                           Font lf, Font vf, Color bg) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setBackgroundColor(bg); lc.setPadding(5f);
        t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(value, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setBackgroundColor(bg); vc.setPadding(5f);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(vc);
    }

    private void left(Document doc, String text, Font font) throws Exception {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_LEFT);
        doc.add(p);
    }

    private void right(Document doc, String text, Font font) throws Exception {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        doc.add(p);
    }

    private Paragraph spacer() { return new Paragraph(" "); }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String fmtQty(double q) {
        return q == Math.floor(q) ? String.valueOf((int) q) : String.valueOf(q);
    }

    private NumberFormat currencyFmt() {
        NumberFormat f = NumberFormat.getCurrencyInstance(new Locale("pt", "PT"));
        f.setCurrency(Currency.getInstance("EUR"));
        return f;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
