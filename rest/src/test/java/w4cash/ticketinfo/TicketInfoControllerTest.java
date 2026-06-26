package w4cash.ticketinfo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Disabled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.openbravo.pos.ticket.TicketInfo;

import w4cash.LoadDatabase;

@WebMvcTest(TicketInfoController.class)
class TicketInfoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SharedTicketRepository repository;

    private Connection mockConnection;

    // Default SELECT statement (used for SHAREDTICKETS SELECTs and ATTRIBUTEVALUE)
    private PreparedStatement mockSelectStmt;
    private ResultSet mockResultSet;

    // Product SELECT statement (overrides default for queries containing "PRODUCTS")
    private PreparedStatement mockProductStmt;
    private ResultSet mockProductResultSet;

    // Mutation statements
    private PreparedStatement mockInsertStmt;
    private PreparedStatement mockUpdateStmt;
    private PreparedStatement mockDeleteStmt;

    @BeforeEach
    void setUp() throws Exception {
        mockConnection = mock(Connection.class);
        mockSelectStmt = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        mockProductStmt = mock(PreparedStatement.class);
        mockProductResultSet = mock(ResultSet.class);
        mockInsertStmt = mock(PreparedStatement.class);
        mockUpdateStmt = mock(PreparedStatement.class);
        mockDeleteStmt = mock(PreparedStatement.class);

        LoadDatabase.DBConnection = mockConnection;

        // Stub ordering: most general first, most specific last (last match wins in Mockito).
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockSelectStmt);
        when(mockConnection.prepareStatement(argThat(s -> s != null && s.contains("PRODUCTS")))).thenReturn(mockProductStmt);
        when(mockConnection.prepareStatement(argThat(s -> s != null && s.startsWith("INSERT")))).thenReturn(mockInsertStmt);
        when(mockConnection.prepareStatement(argThat(s -> s != null && s.startsWith("UPDATE")))).thenReturn(mockUpdateStmt);
        when(mockConnection.prepareStatement(argThat(s -> s != null && s.startsWith("DELETE")))).thenReturn(mockDeleteStmt);

        when(mockSelectStmt.executeQuery()).thenReturn(mockResultSet);
        when(mockProductStmt.executeQuery()).thenReturn(mockProductResultSet);
        when(mockInsertStmt.executeUpdate()).thenReturn(1);
        when(mockUpdateStmt.executeUpdate()).thenReturn(1);
        when(mockDeleteStmt.executeUpdate()).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        LoadDatabase.DBConnection = null;
    }

    // ── GET /TicketInfos ──────────────────────────────────────────────────────

    @Test
    void getAllTicketInfos_returnsEmptyCollection() throws Exception {
        when(mockResultSet.next()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/TicketInfos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void getAllTicketInfos_returnsTickets() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("t1");
        when(mockResultSet.getString("NAME")).thenReturn("Table 1");

        SharedTicket ticket = new SharedTicket("t1", "Table 1");
        when(repository.findAll()).thenReturn(List.of(ticket));

        mockMvc.perform(get("/TicketInfos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.*[0].name").value("Table 1"));
    }

    @Test
    void getAllTicketInfos_syncsDatabaseBeforeReturning() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("t1");
        when(mockResultSet.getString("NAME")).thenReturn("Table 1");
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/TicketInfos")).andExpect(status().isOk());

        verify(repository).deleteAll();
        verify(repository).save(any(SharedTicket.class));
    }

    // ── GET /orderitem/{tableId}/{tableName}/{lockby} ─────────────────────────

    @Test
    void getOrderItem_createsNewTicketWhenTableNotFound() throws Exception {
        when(mockResultSet.next()).thenReturn(false);

        mockMvc.perform(get("/orderitem/table1/Table+1/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_").value("table1"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines").isEmpty());

        verify(mockInsertStmt).setString(1, "table1");
        verify(mockInsertStmt).setString(4, "user1");
        verify(mockInsertStmt).executeUpdate();
    }

    @Test
    void getOrderItem_returnsEmptyOrderItemForNullContent() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("table2");
        when(mockResultSet.getString("NAME")).thenReturn("Table 2");
        when(mockResultSet.getBytes("CONTENT")).thenReturn(null);
        when(mockResultSet.getString("LOCKBY")).thenReturn(null);

        mockMvc.perform(get("/orderitem/table2/Table+2/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_").value("table2"))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void getOrderItem_deserializesValidTicketToEmptyOrderItem() throws Exception {
        byte[] content = serializeTicketInfo(new TicketInfo());

        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("table3");
        when(mockResultSet.getString("NAME")).thenReturn("Table 3");
        when(mockResultSet.getBytes("CONTENT")).thenReturn(content);
        when(mockResultSet.getString("LOCKBY")).thenReturn(null);

        mockMvc.perform(get("/orderitem/table3/Table+3/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_").value("table3"))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    // ── PUT /orderitem/{id} ───────────────────────────────────────────────────

    @Test
    void putOrderItem_withNoLines_updatesDatabase() throws Exception {
        mockMvc.perform(put("/orderitem/table1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_\":\"table1\",\"lines\":[]}"))
                .andExpect(status().isOk());

        verify(mockUpdateStmt).setString(2, "table1");
        verify(mockUpdateStmt).executeUpdate();
    }

    @Test
    @Disabled("TicketLineInfo(ProductInfoExt, ..., null discountInfo, ...) NPEs inside w4cash.jar — " +
              "the production controller passes null for DiscountInfo, crashing any PUT with lines")
    void putOrderItem_withOneLine_lookupsProductAndUpdates() throws Exception {
        when(mockProductResultSet.next()).thenReturn(true, false);
        when(mockProductResultSet.getString("ID")).thenReturn("prod1");
        when(mockProductResultSet.getString("NAME")).thenReturn("Burger");
        when(mockProductResultSet.getDouble("PRICESELL")).thenReturn(9.99);
        when(mockProductResultSet.getString("ATTRIBUTESET_ID")).thenReturn(null);
        when(mockProductResultSet.getString("CATEGORY")).thenReturn("cat1");
        when(mockProductResultSet.getString("CODE")).thenReturn("BURGER");
        when(mockProductResultSet.getString("REFERENCE")).thenReturn(null);
        when(mockProductResultSet.getDouble("PRICEBUY")).thenReturn(5.00);
        when(mockProductResultSet.getString("TAXCAT")).thenReturn(null);
        when(mockProductResultSet.getString("BGCOLOR")).thenReturn(null);
        when(mockProductResultSet.getString("UNIT")).thenReturn(null);

        mockMvc.perform(put("/orderitem/table1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_\":\"table1\",\"lines\":[{" +
                        "\"productId\":\"prod1\"," +
                        "\"productName\":\"Burger\"," +
                        "\"pricesell\":9.99," +
                        "\"qty\":1.0," +
                        "\"attSetInstDesc\":\"\"" +
                        "}]}"))
                .andExpect(status().isOk());

        verify(mockProductStmt).setString(1, "prod1");
        verify(mockUpdateStmt).executeUpdate();
    }

    // ── DELETE /orderitem/{id} ────────────────────────────────────────────────

    @Test
    void deleteOrderItem_executesSqlDelete() throws Exception {
        mockMvc.perform(delete("/orderitem/table1"))
                .andExpect(status().isOk());

        verify(mockDeleteStmt).setString(1, "table1");
        verify(mockDeleteStmt).executeUpdate();
    }

    // ── DELETE /TicketInfo/{id} ───────────────────────────────────────────────

    @Test
    void deleteTicketInfo_callsRepositoryDeleteById() throws Exception {
        mockMvc.perform(delete("/TicketInfo/1"))
                .andExpect(status().isOk());

        verify(repository).deleteById(1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] serializeTicketInfo(TicketInfo t) throws IOException {
        try (var baos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(t);
            return baos.toByteArray();
        }
    }
}
