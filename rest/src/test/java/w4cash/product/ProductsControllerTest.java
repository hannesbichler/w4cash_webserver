package w4cash.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductsController.class)
class ProductsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductsRepository repository;

    @Test
    void getAll_returnsEmptyCollection() throws Exception {
        when(repository.findAll(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void getAll_returnsProducts() throws Exception {
        Product product = new Product("prod1", "REF1", "BURGER", "Burger", 5.00, 9.99, "tax1", "cat1", "unit",
                "attrset1");
        when(repository.findAll(isNull())).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.*[0].id_").value("prod1"))
                .andExpect(jsonPath("$._embedded.*[0].name").value("Burger"))
                .andExpect(jsonPath("$._embedded.*[0].pricesell").value(9.99))
                .andExpect(jsonPath("$._embedded.*[0].attributeSetId").value("attrset1"));
    }

    @Test
    void getAll_passesCategoryIdFilterToRepository() throws Exception {
        when(repository.findAll(eq("drinks"))).thenReturn(List.of());

        mockMvc.perform(get("/products").param("categoryId", "drinks"))
                .andExpect(status().isOk());

        verify(repository).findAll("drinks");
    }

    @Test
    void getAll_repositoryError_returns500() throws Exception {
        when(repository.findAll(any())).thenThrow(new SQLException("db down"));

        mockMvc.perform(get("/products"))
                .andExpect(status().isInternalServerError());
    }

}
