package w4cash.floors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

@RestController
class FloorController {

	private static final Logger logger = LoggerFactory.getLogger(FloorController.class);

	private final FloorsRepository repository;

	FloorController(FloorsRepository repository) {
		this.repository = repository;
	}

	// Reads FLOORS straight through. This used to wipe and repopulate a JPA
	// mirror in in-memory H2 on every call, which meant two concurrent requests
	// could each observe the other's half-built table.
	@GetMapping("/floors")
	CollectionModel<EntityModel<Floor>> all() {
		List<EntityModel<Floor>> floors = new ArrayList<>();
		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn.prepareStatement(
						"SELECT ID, NAME, SORTORDER FROM FLOORS ORDER BY SORTORDER NULLS LAST, NAME");
				ResultSet rs = st.executeQuery()) {
			while (rs.next()) {
				int sortOrder = rs.getInt("SORTORDER");
				floors.add(EntityModel.of(new Floor(
						rs.getString("ID"),
						rs.getString("NAME"),
						rs.wasNull() ? null : sortOrder)));
			}
		} catch (SQLException e) {
			logger.error("Failed to load floors", e);
		}

		return CollectionModel.of(floors, linkTo(methodOn(FloorController.class).all()).withSelfRel());
	}

}
