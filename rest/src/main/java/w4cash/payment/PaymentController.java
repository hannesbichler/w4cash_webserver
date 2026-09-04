package w4cash.payment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.payment.PaymentInfo;
import com.openbravo.pos.payment.PaymentInfoCash;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import com.openbravo.pos.ticket.TicketTaxInfo;

import w4cash.LoadDatabase;
//import w4cash.print.TicketPrintService;
import w4cash.ticketinfo.OrderItem;
import w4cash.ticketinfo.OrderLine;

@RestController
class PaymentController {

    // private final TicketPrintService ticketPrintService;
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    // PaymentController(TicketPrintService ticketPrintService) {
    // this.ticketPrintService = ticketPrintService;
    // }

    // Mirrors com.openbravo.pos.forms.DataLogicSales#saveTicket, minus RKSV
    // signing, TAXLINES and stock/inventory updates (see plan doc for scope).
    @PostMapping("/payment/{activeCashId}")
    ResponseEntity<?> postPayment(@PathVariable("activeCashId") String activeCashId,
            @RequestBody OrderItem newOrderItem) {
        logger.info("Payment: activeCashId={}, kellner={}, placeId={}", activeCashId, newOrderItem.getKellner(),
                newOrderItem.getId_());

        // A single connection for the whole payment: the write below must be one
        // atomic unit, and LOCK TABLE only excludes other requests because this
        // connection is exclusive to this one.
        try (Connection conn = LoadDatabase.getConnection()) {

            List<TicketLineInfo> lines = new ArrayList<>();
            double total = 0.0;
            if (newOrderItem.getLines() != null) {
                for (OrderLine line : newOrderItem.getLines()) {
                    if (line.getQty() <= 0) {
                        logger.warn("Skipping line with non-positive quantity: productId={}, productName={}, qty={}",
                                line.getProductId(), line.getProductName(), line.getQty());
                        continue;
                    }
                    total += line.getQty() * line.getPricesell();
                    var proinfoext = new com.openbravo.pos.ticket.ProductInfoExt();
                    proinfoext.setID(line.getProductId());
                    proinfoext.setName(line.getProductName());
                    proinfoext.setPriceSell(line.getPricesell());

                    try (PreparedStatement st = conn.prepareStatement(
                            "SELECT ID, REFERENCE, CODE, NAME, PRICEBUY, PRICESELL, TAXCAT, CATEGORY, ATTRIBUTESET_ID, BGCOLOR, UNIT "
                                    + "FROM PRODUCTS WHERE ID = ?")) {
                        st.setString(1, line.getProductId());
                        try (ResultSet rs = st.executeQuery()) {
                            if (rs.next()) {
                                proinfoext.setID(rs.getString("ID"));
                                proinfoext.setName(rs.getString("NAME"));
                                proinfoext.setPriceSell(rs.getDouble("PRICESELL"));
                                proinfoext.setAttributeSetID(rs.getString("ATTRIBUTESET_ID"));
                                proinfoext.setCategoryID(rs.getString("CATEGORY"));
                                proinfoext.setCode(rs.getString("CODE"));
                                proinfoext.setReference(rs.getString("REFERENCE"));
                                proinfoext.setPriceBuy(rs.getDouble("PRICEBUY"));
                                proinfoext.setTaxCategoryID(rs.getString("TAXCAT"));
                                proinfoext.setBgColor(rs.getString("BGCOLOR"));
                                proinfoext.setUnit(rs.getString("UNIT"));
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("Failed to fetch product info for id={}", line.getProductId(), e);
                        return ResponseEntity.internalServerError()
                                .body("Failed to fetch product info for id=" + line.getProductId());
                    }
                    var props = new Properties();
                    props.setProperty("Place", newOrderItem.getId_());
                    var ticketLineInfo = new TicketLineInfo(proinfoext, line.getQty(), line.getPricesell(), null,
                            props, false, null, null, null, null);
                    ticketLineInfo.setProductAttSetInstDesc(line.getAttSetInstDesc());
                    lines.add(ticketLineInfo);
                }
            }

            if (lines.isEmpty()) {
                return ResponseEntity.badRequest().body("No valid lines to pay");
            }

            var ticketInfo = new TicketInfo();
            var paymentInfo = new ArrayList<PaymentInfo>();
            paymentInfo.add(new PaymentInfoCash(total, total));
            ticketInfo.setPayments(paymentInfo);

            conn.setAutoCommit(false);
            try {
                // lock exclusive at start with timeout, same as DataLogicSales#saveTicket
                try (PreparedStatement lock = conn
                        .prepareStatement("LOCK TABLE TICKETS IN EXCLUSIVE MODE WAIT 5")) {
                    lock.execute();
                }

                // Allocate the next ticket number under the lock. Previously every
                // request shared one Oracle session, so the lock was a no-op between
                // requests and two concurrent payments could read the same MAX.
                try (PreparedStatement st = conn
                        .prepareStatement("SELECT MAX(TICKETID)+1 FROM TICKETS WHERE TICKETID IS NOT NULL");
                        ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        int nextId = rs.getInt(1);
                        if (!rs.wasNull()) {
                            ticketInfo.setTicketId(nextId);
                        }
                    }
                }

                ticketInfo.setActiveCash(activeCashId);

                // resolve waiter name -> PEOPLE.ID, same lookup as
                // TicketPrintService#queryPersonByName
                String personId = null;
                try (PreparedStatement st = conn.prepareStatement("SELECT ID FROM PEOPLE WHERE NAME = ?")) {
                    st.setString(1, newOrderItem.getKellner());
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) {
                            personId = rs.getString("ID");
                        }
                    }
                }
                if (personId == null) {
                    conn.rollback();
                    return ResponseEntity.badRequest().body("Unknown kellner=" + newOrderItem.getKellner());
                }

                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO RECEIPTS (ID, MONEY, DATENEW, Attributes) VALUES (?, ?, ?, ?)")) {
                    st.setString(1, ticketInfo.getId());
                    st.setString(2, ticketInfo.getActiveCash());
                    st.setTimestamp(3, new Timestamp(ticketInfo.getDate().getTime()));
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    ticketInfo.getProperties().storeToXML(o, AppLocal.APP_NAME, "UTF-8");
                    st.setBytes(4, o.toByteArray()); // Set Attributes column, adjust as needed
                    st.executeUpdate();
                } catch (IOException e) {
                    logger.error("Failed to insert receipt for tableId={}", newOrderItem.getId_(), e);
                    conn.rollback();
                    return ResponseEntity.internalServerError().body("Failed to insert receipt: " + e.getMessage());
                }

                // signature-related columns (SIGNATUREID, SIGNATUREVALUE, CASHTICKETID,
                // CASHSUMCOUNTER, ALGORITHMID, POSID, SIGNATUREOUTOFORDER, CHAINVALUE,
                // CASHSUMCOUNTERENC) are intentionally left NULL: RKSV signing stays the
                // desktop app's responsibility for now.
                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO TICKETS (ID, TICKETTYPE, TICKETID, PERSON, CUSTOMER, VALIDATION, MONTH) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    st.setString(1, ticketInfo.getId());
                    st.setInt(2, TicketInfo.RECEIPT_NORMAL);
                    st.setInt(3, ticketInfo.getTicketId());
                    st.setString(4, personId);
                    st.setString(5, ticketInfo.getCustomerId());
                    Integer validation = ticketInfo.getValidation();
                    if (validation == null) {
                        st.setNull(6, Types.INTEGER);
                    } else {
                        st.setInt(6, validation);
                    }
                    Integer month = ticketInfo.getMonth();
                    if (month == null) {
                        st.setNull(7, Types.INTEGER);
                    } else {
                        st.setInt(7, month);
                    }
                    st.executeUpdate();
                }

                int lineNo = 0;
                try (PreparedStatement st = conn.prepareStatement(
                        // TICKET, LINE, PRODUCT, ATTRIBUTESETINSTANCE_ID, UNITS, PRICE, TAXID,
                        // ATTRIBUTES, UNIT, ATTR1, ATTR2, ATTR3, ATTR4
                        "INSERT INTO TICKETLINES (TICKET, LINE, PRODUCT, ATTRIBUTESETINSTANCE_ID, UNITS, PRICE, TAXID, ATTRIBUTES, UNIT, ATTR1, ATTR2, ATTR3, ATTR4) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (TicketLineInfo l : lines) {
                        try {
                            String taxId = resolveTaxId(conn, l.getProductTaxCategoryID(), ticketInfo.getDate());
                            if (taxId == null) {
                                conn.rollback();
                                return ResponseEntity.badRequest()
                                        .body("No tax rate configured for product " + l.getProductID());
                            }
                            st.setString(1, ticketInfo.getId());
                            st.setInt(2, lineNo++);
                            st.setString(3, l.getProductID());
                            st.setString(4, l.getProductAttSetInstId());
                            st.setDouble(5, l.getMultiply());
                            st.setDouble(6, l.getPrice());
                            st.setString(7, taxId);
                            ByteArrayOutputStream o = new ByteArrayOutputStream();
                            l.getProperties().storeToXML(o, AppLocal.APP_NAME, "UTF-8");
                            st.setBytes(8, o.toByteArray());
                            st.setString(9, "x");
                            st.setNull(10, Types.VARCHAR);
                            st.setNull(11, Types.VARCHAR);
                            st.setNull(12, Types.VARCHAR);
                            st.setNull(13, Types.VARCHAR);
                            st.addBatch();
                        } catch (IOException e) {
                            logger.error("Failed to prepare ticket line for productId={}", l.getProductID(), e);
                            conn.rollback();
                            return ResponseEntity.internalServerError()
                                    .body("Failed to prepare ticket line for productId=" + l.getProductID());
                        }
                    }
                    st.executeBatch();
                }

                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO PAYMENTS (ID, RECEIPT, PAYMENT, TOTAL, TRANSID, RETURNMSG) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (PaymentInfo p : ticketInfo.getPayments()) {
                        st.setString(1, UUID.randomUUID().toString());
                        st.setString(2, ticketInfo.getId());
                        st.setString(3, p.getName());
                        st.setDouble(4, p.getTotal());
                        st.setString(5, ticketInfo.getTransactionID());
                        st.setBytes(6, "OK".getBytes());
                        st.addBatch();
                    }
                    st.executeBatch();
                }

                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO TAXLINES (ID, RECEIPT, TAXID, BASE, AMOUNT)  VALUES (?, ?, ?, ?, ?)")) {
                    if (ticketInfo.getTaxes() != null) {
                        for (final TicketTaxInfo tickettax : ticketInfo.getTaxes()) {
                            st.setString(1, UUID.randomUUID().toString());
                            st.setString(2, ticketInfo.getId());
                            st.setString(3, tickettax.getTaxInfo().getId());
                            st.setDouble(4, tickettax.getSubTotal());
                            st.setDouble(5, tickettax.getTax());
                            st.addBatch();
                        }
                    }
                    st.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            // autoCommit is reset by the pool when the connection is returned.

        } catch (SQLException e) {
            logger.error("Failed to save payment for tableId={}", newOrderItem.getId_(), e);
            return ResponseEntity.internalServerError().body("Failed to save payment: " + e.getMessage());
        }

        return ResponseEntity.ok(newOrderItem);
    }

    // Minimal equivalent of TaxesLogic#getTaxInfo for the no-customer/default
    // case: resolves which TAXES row currently applies to a product's tax
    // category. Does not compute amounts or write TAXLINES.
    private String resolveTaxId(Connection conn, String taxCategoryId, java.util.Date date) throws SQLException {
        if (taxCategoryId == null) {
            return null;
        }
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT ID FROM ("
                        + "SELECT ID FROM TAXES WHERE PARENTID IS NULL AND CATEGORY = ? AND CUSTCATEGORY IS NULL AND VALIDFROM <= ? "
                        + "ORDER BY VALIDFROM DESC"
                        + ") WHERE ROWNUM = 1")) {
            st.setString(1, taxCategoryId);
            st.setTimestamp(2, new Timestamp(date.getTime()));
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ID");
                }
            }
        }
        return null;
    }
}
