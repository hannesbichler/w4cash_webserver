package w4cash.print;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.openbravo.pos.printer.escpos.DevicePrinterPlain;

import w4cash.LoadDatabase;
import w4cash.W4cashApplication;
import w4cash.ticketinfo.OrderItem;

@Service
public class TicketPrintService {

    private static final Logger logger = LoggerFactory.getLogger(TicketPrintService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int LINE_WIDTH = 40;
    private static final String DIVIDER = "─".repeat(LINE_WIDTH);

    TicketPrintService() {
    }

    public void printOrderTicket(String tableId, @NonNull OrderItem orderItem) {
        int lineCount = orderItem != null && orderItem.getLines() != null ? orderItem.getLines().size() : 0;
        String printerName = queryPrinterName();
        if (printerName == null) {
            logger.warn("No printer configured in PRINTERS table — skipping print for tableId={}", tableId);
            return;
        }

        PrintService printService = findPrintService(printerName);
        if (printService == null) {
            logger.warn("PrintService '{}' not found on this system — skipping print for tableId={}", printerName,
                    tableId);
            return;
        }

        String tableName = queryTableName(tableId);
        logger.info("Starting ticket print for tableId={}, tableName='{}', lines={}, configuredPrinter='{}'",
                tableId,
                tableName,
                lineCount,
                printerName);

        try {
            if (isPdfPrintService(printService)) {
                String content = buildPlainTicket(tableName, orderItem);
                if (content.isBlank()) {
                    logger.info("Ticket content is empty for tableId={}, skipping PDF print", tableId);
                    return;
                }
                printPlainText(printService, content);
                logger.info("Ticket printed via PDF fallback for tableId={} on printer '{}'", tableId, printerName);
                return;
            }

            PrinterWritterPrintService writer = new PrinterWritterPrintService(printService);
            DevicePrinterPlain printer = new DevicePrinterPlain(writer);
            printer.beginReceipt();
            printer.printText(0, "Tisch: " + tableName);
            printer.printText(0, LocalDateTime.now().format(TIMESTAMP));
            printer.printText(0, DIVIDER);
            orderItem.getLines().forEach(line -> {
                int qty = (int) line.getNewQty();
                if (qty == 0) {
                    return;
                }
                String name = line.getProductName() != null ? line.getProductName() : "";
                try {
                    printer.printText(0, qty + "x  " + name);
                    String att = line.getAttSetInstDesc();
                    if (att != null && !att.isBlank()) {
                        printer.printText(0, "    [" + att.trim() + "]");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            printer.printText(0, DIVIDER);
            printer.endReceipt();
            logger.debug("Receipt ended for tableId={}, closing writer", tableId);
            writer.close();
            logger.info("Ticket printed for tableId={} on printer '{}'", tableId, printerName);
        } catch (Exception e) {
            logger.warn("Ticket print failed for tableId={}: {}", tableId, e.getMessage(), e);
        }
    }

    private boolean isPdfPrintService(PrintService printService) {
        String serviceName = printService.getName() == null ? "" : printService.getName().toLowerCase();
        if (serviceName.contains("pdf")) {
            logger.debug("PDF fallback triggered by printer name match: '{}'", printService.getName());
            return true;
        }

        for (DocFlavor flavor : printService.getSupportedDocFlavors()) {
            String mimeType = flavor.getMimeType();
            if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
                logger.debug("PDF fallback triggered by MIME flavor match: printer='{}', flavor='{}'",
                        printService.getName(),
                        mimeType);
                return true;
            }
        }

        logger.debug("PDF fallback not triggered: printer='{}'", printService.getName());
        return false;
    }

    private String buildPlainTicket(String tableName, @NonNull OrderItem orderItem) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sbLines = new StringBuilder();

        if (orderItem != null && orderItem.getLines() != null) {
            orderItem.getLines().forEach(line -> {
                int qty = (int) line.getNewQty();
                if (qty == 0) {
                    return;
                }
                String name = line.getProductName() != null ? line.getProductName() : "";
                sbLines.append(qty).append("x  ").append(name).append(System.lineSeparator());
                String att = line.getAttSetInstDesc();
                if (att != null && !att.isBlank()) {
                    sbLines.append("    [").append(att.trim()).append(']').append(System.lineSeparator());
                }
            });
        }
        if (sbLines.length() > 0) {
            sb.append("Tisch: ").append(tableName).append(System.lineSeparator());
            sb.append("KellnerIn: ").append(orderItem.getKellner()).append(System.lineSeparator());
            sb.append(LocalDateTime.now().format(TIMESTAMP)).append(System.lineSeparator());
            sb.append(DIVIDER).append(System.lineSeparator());
            sb.append(sbLines);
            sb.append(DIVIDER).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void printPlainText(PrintService printService, String content) throws PrintException {
        DocPrintJob job = printService.createPrintJob();
        Doc doc;
        DocFlavor selectedFlavor;

        if (printService.isDocFlavorSupported(DocFlavor.SERVICE_FORMATTED.PRINTABLE)) {
            selectedFlavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
            doc = new SimpleDoc(new PlainTextPrintable(content), selectedFlavor, null);
        } else if (printService.isDocFlavorSupported(DocFlavor.STRING.TEXT_PLAIN)) {
            selectedFlavor = DocFlavor.STRING.TEXT_PLAIN;
            doc = new SimpleDoc(content, selectedFlavor, null);
        } else if (printService.isDocFlavorSupported(DocFlavor.CHAR_ARRAY.TEXT_PLAIN)) {
            selectedFlavor = DocFlavor.CHAR_ARRAY.TEXT_PLAIN;
            doc = new SimpleDoc(content.toCharArray(), selectedFlavor, null);
        } else if (printService.isDocFlavorSupported(DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8)) {
            selectedFlavor = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8;
            doc = new SimpleDoc(content.getBytes(StandardCharsets.UTF_8), selectedFlavor, null);
        } else if (printService.isDocFlavorSupported(DocFlavor.BYTE_ARRAY.TEXT_PLAIN_US_ASCII)) {
            selectedFlavor = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_US_ASCII;
            doc = new SimpleDoc(content.getBytes(StandardCharsets.US_ASCII), selectedFlavor, null);
        } else if (printService.isDocFlavorSupported(DocFlavor.INPUT_STREAM.AUTOSENSE)) {
            selectedFlavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
            doc = new SimpleDoc(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    selectedFlavor,
                    null);
        } else {
            String availableFlavors = Arrays.stream(printService.getSupportedDocFlavors())
                    .map(flavor -> flavor.getRepresentationClassName() + " | " + flavor.getMimeType())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("<none>");
            logger.warn("No supported plain-text DocFlavor for printer '{}'. Supported flavors: {}",
                    printService.getName(),
                    availableFlavors);
            throw new PrintException("No supported plain-text DocFlavor for " + printService.getName());
        }

        job.print(doc, null);
        logger.info("Submitted plain-text print job to '{}' (chars={}, flavor='{}')",
                printService.getName(),
                content.length(),
                selectedFlavor);
    }

    private static final class PlainTextPrintable implements Printable {
        private final String[] lines;

        private PlainTextPrintable(String content) {
            this.lines = content.split("\\R", -1);
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));

            int lineHeight = g2.getFontMetrics().getHeight();
            int ascent = g2.getFontMetrics().getAscent();
            int linesPerPage = Math.max(1, (int) (pageFormat.getImageableHeight() / lineHeight));

            int start = pageIndex * linesPerPage;
            if (start >= lines.length) {
                return NO_SUCH_PAGE;
            }

            float x = (float) pageFormat.getImageableX();
            float y = (float) pageFormat.getImageableY() + ascent;
            int end = Math.min(lines.length, start + linesPerPage);

            for (int i = start; i < end; i++) {
                g2.drawString(lines[i], x, y);
                y += lineHeight;
            }

            return PAGE_EXISTS;
        }
    }

    private String queryPrinterName() {
        // get printer from config
        return W4cashApplication.APP_CONFIG.getProperty("machine.printer");
    }

    private String queryTableName(String tableId) {
        try (PreparedStatement st = LoadDatabase.DBConnection.prepareStatement(
                "SELECT NAME FROM SHAREDTICKETS WHERE ID = ?")) {
            st.setString(1, tableId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("NAME");
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not query table name for id={}: {}", tableId, e.getMessage());
        }
        return tableId;
    }

    private PrintService findPrintService(String name) {
        if (name.contains(":")) {
            name = name.split(":", 2)[1].trim();
        }
        name = name.split(",")[0].trim(); // remove any suffix after comma
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(name)) {
                return service;
            }
        }
        return null;
    }
}
