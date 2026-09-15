package w4cash.attribute;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(AttributeSetsController.class)
class AttributeSetsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AttributeSetsRepository repository;

    @MockBean
    AttributesRepository attributesRepository;

    @Test
    void getOne_returnsDetailWithAttributes() throws Exception {
        var detail = new AttributeSetDetail("set1", "Shirt Variants",
                List.of(new AttributeUseRef("attr1", "Size", 1)));
        when(repository.findById("set1")).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/attribute-sets/set1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shirt Variants"))
                .andExpect(jsonPath("$.attributes[0].name").value("Size"));
    }

    @Test
    void getOne_returns404WhenNotFound() throws Exception {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/attribute-sets/missing"))
                .andExpect(status().isNotFound());
    }

}
