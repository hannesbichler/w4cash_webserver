package w4cash.places;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class PlaceController {

	private final PlaceRepository repository;

	PlaceController(PlaceRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/places/{floorId}")
	CollectionModel<EntityModel<Place>> all(@PathVariable String floorId) {
		List<EntityModel<Place>> places = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME FROM PLACES WHERE FLOOR = ?")) {
			st.setString(1, floorId);
			try (ResultSet rs = st.executeQuery()) {
				repository.deleteAll();
				while (rs.next()) {
					String id = rs.getString("ID");
					String name = rs.getString("NAME");
					this.repository.save(new Place(id, name));
				}
			}
			places = repository.findAll().stream()
					.map(place -> EntityModel.of(place// ,
					// linkTo(methodOn(PlaceController.class).one(place.getId())).withSelfRel(),
					// linkTo(methodOn(PlaceController.class).all()).withRel("places")
					))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			e.printStackTrace();
			// TODO: handle exception
		}

		return CollectionModel.of(places, linkTo(methodOn(PlaceController.class).all(floorId)).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	@GetMapping("/place/{id}")
	EntityModel<Place> one(@PathVariable Long id) {

		Place place = repository.findById(id) //
				.orElseThrow(() -> new PlaceNotFoundException(id));

		return EntityModel.of(place //
		// linkTo(methodOn(PlaceController.class).one(id)).withSelfRel(),
		// linkTo(methodOn(PlaceController.class).all()).withRel("places")
		);
	}
	// end::get-single-item[]

	@PutMapping("/place/{id}")
	Place replacePlace(@RequestBody Place newPlace, @PathVariable Long id) {

		return repository.findById(id) //
				.map(place -> {
					place.setName(newPlace.getName());
					place.setId(newPlace.getId());
					return repository.save(place);
				}) //
				.orElseGet(() -> {
					return repository.save(newPlace);
				});
	}

	@DeleteMapping("/place/{id}")
	void deletePlace(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
