package w4cash.places;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

@RestController
class PlaceController {
	private static final Logger logger = LoggerFactory.getLogger(PlaceController.class);

	private static final String SELECT_COLUMNS = "SELECT ID, NAME, X, Y, FLOOR, WIDTH, HEIGHT, FONTSIZE, FONTCOLOR FROM PLACES";

	private final PlacesRepository repository;

	PlaceController(PlacesRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/places")
	CollectionModel<EntityModel<Place>> all() {
		logger.info("GET /places request was called");
		List<EntityModel<Place>> places = new ArrayList<>();
		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn.prepareStatement(SELECT_COLUMNS + " ORDER BY NAME");
				ResultSet rs = st.executeQuery()) {
			while (rs.next()) {
				places.add(EntityModel.of(map(rs)));
			}
		} catch (SQLException e) {
			logger.error("Failed to load places", e);
		}
		return CollectionModel.of(places, linkTo(methodOn(PlaceController.class).all()).withSelfRel());
	}

	@GetMapping("/places/{floorId}")
	CollectionModel<EntityModel<Place>> all(@PathVariable String floorId) {
		logger.info("GET /places/{} request was called", floorId);
		List<EntityModel<Place>> places = new ArrayList<>();
		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn.prepareStatement(SELECT_COLUMNS + " WHERE FLOOR = ? ORDER BY NAME")) {
			st.setString(1, floorId);
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					places.add(EntityModel.of(map(rs)));
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to load places for floorId={}", floorId, e);
		}

		return CollectionModel.of(places, linkTo(methodOn(PlaceController.class).all(floorId)).withSelfRel());
	}

	private Place map(ResultSet rs) throws SQLException {
		Place place = new Place(rs.getString("ID"), rs.getString("NAME"));
		place.setFloorId(rs.getString("FLOOR"));
		place.setX(rs.getInt("X"));
		place.setY(rs.getInt("Y"));
		place.setWidth(nullableInt(rs, "WIDTH"));
		place.setHeight(nullableInt(rs, "HEIGHT"));
		place.setFontSize(nullableInt(rs, "FONTSIZE"));
		place.setFontColor(rs.getString("FONTCOLOR"));
		return place;
	}

	private Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}
}
