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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import w4cash.ticketinfo.OrderLine;

@Service
public class TicketPrintService {

    private static final Logger logger = LoggerFactory.getLogger(TicketPrintService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int LINE_WIDTH = 40;
    private static final String DIVIDER = "─".repeat(LINE_WIDTH);

    TicketPrintService() {
    }

    public void printOrderTicket(String tableId, @NonNull OrderItem orderItem) {
        var printerNames = queryPrinterNames();
        String tableName = queryTableName(tableId);

        // Group lines by the printer index derived from their product's category
        Map<Integer, List<OrderLine>> linesByPrinter = new HashMap<>();
        if (orderItem != null && orderItem.getLines() != null) {
            for (OrderLine line : orderItem.getLines()) {
                if ((int) line.getNewQty() == 0) {
                    continue;
                }
                int printerIndex = queryPrinterIndexForProduct(line.getProductId());
                linesByPrinter.computeIfAbsent(printerIndex, k -> new ArrayList<>()).add(line);
            }
        }

        if (linesByPrinter.isEmpty()) {
            logger.info("No printable lines for tableId={}", tableId);
            return;
        }

        String kellner = orderItem != null ? orderItem.getKellner() : "";

        for (Map.Entry<Integer, List<OrderLine>> entry : linesByPrinter.entrySet()) {
            int printerIndex = entry.getKey();
            List<OrderLine> lines = entry.getValue();
            String printerName = printerNames.get(printerIndex);
            if (printerName == null || printerName.isBlank()) {
                logger.warn("No printer configured for index={} — skipping {} lines for tableId={}",
                        printerIndex, lines.size(), tableId);
                continue;
            }

            PrintService printService = findPrintService(printerName);
            if (printService == null) {
                logger.warn("PrintService '{}' not found — skipping printer index={} for tableId={}",
                        printerName, printerIndex, tableId);
                continue;
            }

            logger.info("Printing ticket for tableId={}, printer index={} ('{}'), lines={}",
                    tableId, printerIndex, printerName, lines.size());
            printTicketForLines(tableId, tableName, kellner, lines, printService, printerName);
        }
    }

    private void printTicketForLines(String tableId, String tableName, String kellner,
            List<OrderLine> lines, PrintService printService, String printerName) {
        try {
            if (isPdfPrintService(printService)) {
                String content = buildPlainTicket(tableName, kellner, lines);
                if (content.isBlank()) {
                    logger.info("Ticket content is empty for tableId={} on printer '{}', skipping", tableId, printerName);
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
            for (OrderLine line : lines) {
                int qty = (int) line.getNewQty();
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
            }
            printer.printText(0, DIVIDER);
            printer.endReceipt();
            writer.close();
            logger.info("Ticket printed for tableId={} on printer '{}'", tableId, printerName);
        } catch (Exception e) {
            logger.warn("Ticket print failed for tableId={} on printer '{}': {}", tableId, printerName, e.getMessage(), e);
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

    private String buildPlainTicket(String tableName, String kellner, List<OrderLine> lines) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sbLines = new StringBuilder();

        for (OrderLine line : lines) {
            int qty = (int) line.getNewQty();
            if (qty == 0) {
                continue;
            }
            String name = line.getProductName() != null ? line.getProductName() : "";
            sbLines.append(qty).append("x  ").append(name).append(System.lineSeparator());
            String att = line.getAttSetInstDesc();
            if (att != null && !att.isBlank()) {
                sbLines.append("    [").append(att.trim()).append(']').append(System.lineSeparator());
            }
        }

        if (sbLines.length() > 0) {
            sb.append("Tisch: ").append(tableName).append(System.lineSeparator());
            sb.append("KellnerIn: ").append(kellner).append(System.lineSeparator());
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

    private Map<Integer, String> queryPrinterNames() {
        var printersConfig = new HashMap<Integer, String>();
        printersConfig.put(1, W4cashApplication.APP_CONFIG.getProperty("machine.printer"));
        printersConfig.put(2, W4cashApplication.APP_CONFIG.getProperty("machine.printer.2"));
        printersConfig.put(3, W4cashApplication.APP_CONFIG.getProperty("machine.printer.3"));
        return printersConfig;
    }

    private int queryPrinterIndexForProduct(String productId) {
        try (PreparedStatement st = LoadDatabase.DBConnection.prepareStatement(
                "SELECT c.PRINTER FROM PRODUCTS p JOIN CATEGORIES c ON p.CATEGORY = c.ID WHERE p.ID = ?")) {
            st.setString(1, productId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    int p = rs.getInt("PRINTER");
                    return p > 0 ? p : 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not query printer index for productId={}: {}", productId, e.getMessage());
        }
        return 1;
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
        name = name.split(",")[0].trim();
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(name)) {
                return service;
            }
        }
        return null;
    }
}
