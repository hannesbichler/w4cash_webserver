package w4cash.person;

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

@WebMvcTest(PersonController.class)
class PersonControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PersonRepository repository;

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

        mockMvc.perform(get("/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void getAll_returnsPersons() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("p1");
        when(mockResultSet.getString("NAME")).thenReturn("Alice");
        when(mockResultSet.getString("APPPASSWORD")).thenReturn("secret");
        when(mockResultSet.getString("CARD")).thenReturn("1234");
        when(mockResultSet.getString("ROLE")).thenReturn("admin");

        Person alice = new Person("p1", "Alice", "secret", "1234", "admin", "");
        alice.setId(1L);
        when(repository.findAll()).thenReturn(List.of(alice));

        mockMvc.perform(get("/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.*[0].name").value("Alice"))
                .andExpect(jsonPath("$._embedded.*[0].role").value("admin"));
    }

    @Test
    void getAll_syncsDatabaseBeforeReturning() throws Exception {
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("ID")).thenReturn("p1");
        when(mockResultSet.getString("NAME")).thenReturn("Bob");
        when(mockResultSet.getString("APPPASSWORD")).thenReturn("pw");
        when(mockResultSet.getString("CARD")).thenReturn("card");
        when(mockResultSet.getString("ROLE")).thenReturn("cashier");
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/persons")).andExpect(status().isOk());

        verify(repository).deleteAll();
        verify(repository).save(any(Person.class));
    }

    @Test
    void getById_returnsPerson() throws Exception {
        Person alice = new Person("p1", "Alice", "secret", "1234", "admin", "");
        alice.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(alice));

        mockMvc.perform(get("/persons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$._links.self").exists())
                .andExpect(jsonPath("$._links.employees").exists());
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/persons/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesExistingPerson() throws Exception {
        Person existing = new Person("p1", "Alice", "old", "card", "admin", "");
        existing.setId(1L);
        Person updated = new Person("p1", "Alice Updated", "new", "card", "admin", "");
        updated.setId(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(updated);

        mockMvc.perform(put("/persons/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice Updated\",\"apppassword\":\"new\",\"card\":\"card\",\"role\":\"admin\",\"image\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"));
    }

    @Test
    void put_createsPersonWhenNotFound() throws Exception {
        Person newPerson = new Person("p2", "Bob", "pw", "card2", "cashier", "");
        newPerson.setId(2L);

        when(repository.findById(2L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(newPerson);

        mockMvc.perform(put("/persons/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"apppassword\":\"pw\",\"card\":\"card2\",\"role\":\"cashier\",\"image\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void delete_callsRepositoryDeleteById() throws Exception {
        mockMvc.perform(delete("/persons/1"))
                .andExpect(status().isOk());

        verify(repository).deleteById(1L);
    }
}
