package w4cash.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import w4cash.LoadDatabase;

@WebMvcTest(ProductsController.class)
class ProductsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductsRepository repository;

    private Connection mockConnection;
    private PreparedStatement mockStatement;
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws Exception {
        mockConnection = mock(Connection.class);
        mockStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);

        LoadDatabase.DBConnection = mockConnection;
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
    }

    @AfterEach
    void tearDown() {
        LoadDatabase.DBConnection = null;
    }

    @Test
    void getAll_returnsEmptyCollection() throws Exception {
        when(mockResultSet.next()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void getAll_returnsProducts() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("prod1");
        when(mockResultSet.getString("CODE")).thenReturn("BURGER");
        when(mockResultSet.getString("NAME")).thenReturn("Burger");
        when(mockResultSet.getFloat("PRICESELL")).thenReturn(9.99f);
        when(mockResultSet.getString("CATEGORY")).thenReturn("cat1");
        when(mockResultSet.getString("ATTRIBUTESET_ID")).thenReturn(null);

        Product product = new Product("prod1", "BURGER", "Burger", 9.99f, "cat1", null);
        product.setId(1L);
        when(repository.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.*[0].name").value("Burger"))
                .andExpect(jsonPath("$._embedded.*[0].pricesell").value(9.99));
    }

    @Test
    void getAll_htmlEscapesProductNames() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("prod2");
        when(mockResultSet.getString("CODE")).thenReturn("<test>");
        when(mockResultSet.getString("NAME")).thenReturn("Café & Bar");
        when(mockResultSet.getFloat("PRICESELL")).thenReturn(5.00f);
        when(mockResultSet.getString("CATEGORY")).thenReturn("cat1");
        when(mockResultSet.getString("ATTRIBUTESET_ID")).thenReturn(null);

        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products")).andExpect(status().isOk());

        // Verifies HtmlUtils.htmlEscape was applied before persisting
        verify(repository).save(argThat(p ->
                p.getCode().equals("&lt;test&gt;") && p.getName().equals("Caf&eacute; &amp; Bar")));
    }

    @Test
    void getAll_syncsDatabaseBeforeReturning() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("prod1");
        when(mockResultSet.getString("CODE")).thenReturn("COLA");
        when(mockResultSet.getString("NAME")).thenReturn("Cola");
        when(mockResultSet.getFloat("PRICESELL")).thenReturn(2.50f);
        when(mockResultSet.getString("CATEGORY")).thenReturn("drinks");
        when(mockResultSet.getString("ATTRIBUTESET_ID")).thenReturn(null);
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products")).andExpect(status().isOk());

        verify(repository).deleteAll();
        verify(repository).save(any(Product.class));
    }

    @Test
    void getByCategory_returnsFilteredProducts() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("prod2");
        when(mockResultSet.getString("CODE")).thenReturn("COLA");
        when(mockResultSet.getString("NAME")).thenReturn("Cola");
        when(mockResultSet.getFloat("PRICESELL")).thenReturn(2.50f);
        when(mockResultSet.getString("ATTRIBUTESET_ID")).thenReturn(null);

        Product product = new Product("prod2", "COLA", "Cola", 2.50f, "drinks", null);
        product.setId(2L);
        when(repository.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/products/drinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.*[0].name").value("Cola"));
    }

    @Test
    void getByCategory_passesCategoryIdAsQueryParameter() throws Exception {
        when(mockResultSet.next()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products/cat1")).andExpect(status().isOk());

        verify(mockStatement).setString(1, "cat1");
    }

    @Test
    void getById_returnsProduct() throws Exception {
        Product product = new Product("prod1", "BURGER", "Burger", 9.99f, "cat1", null);
        product.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Burger"))
                .andExpect(jsonPath("$.pricesell").value(9.99));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/product/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesExistingProduct() throws Exception {
        Product existing = new Product("prod1", "BURGER", "Burger", 9.99f, "food", null);
        existing.setId(1L);
        Product updated = new Product("prod1", "BURGER", "Premium Burger", 12.99f, "food", null);
        updated.setId(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(updated);

        mockMvc.perform(put("/product/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Premium Burger\",\"code\":\"BURGER\",\"pricesell\":12.99,\"categoryId\":\"food\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Premium Burger"));
    }

    @Test
    void put_createsProductWhenNotFound() throws Exception {
        Product newProduct = new Product("prod3", "FRIES", "Fries", 3.50f, "food", null);
        newProduct.setId(3L);

        when(repository.findById(3L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(newProduct);

        mockMvc.perform(put("/product/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Fries\",\"code\":\"FRIES\",\"pricesell\":3.50,\"categoryId\":\"food\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_callsRepositoryDeleteById() throws Exception {
        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isOk());

        verify(repository).deleteById(1L);
    }
}
