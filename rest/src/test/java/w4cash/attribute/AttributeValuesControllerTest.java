package w4cash.attribute;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(AttributeValuesController.class)
class AttributeValuesControllerTest {

        @Autowired
        MockMvc mockMvc;

        @MockBean
        AttributeValuesRepository repository;

        @MockBean
        AttributesRepository attributesRepository;

        @Test
        void getAll_returnsValuesInOrder() throws Exception {
                when(attributesRepository.findById("attr1")).thenReturn(Optional.of(new AttributeRef("attr1", "Size")));
                when(repository.findAllForAttribute("attr1")).thenReturn(List.of(
                                new AttributeValueRef("v1", "S", 1), new AttributeValueRef("v2", "M", 2)));

                mockMvc.perform(get("/attributes/attr1/values"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].value").value("S"))
                                .andExpect(jsonPath("$[1].value").value("M"));
        }

        @Test
        void getAll_returns404WhenAttributeMissing() throws Exception {
                when(attributesRepository.findById("missing")).thenReturn(Optional.empty());

                mockMvc.perform(get("/attributes/missing/values"))
                                .andExpect(status().isNotFound());
        }

}
