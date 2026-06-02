package w4cash.floors;

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
class FloorController {

	private final FloorRepository repository;

	FloorController(FloorRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/floors")
	CollectionModel<EntityModel<Floor>> all() {
		List<EntityModel<Floor>> floors = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME FROM FLOORS");
				ResultSet rs = st.executeQuery()) {
			repository.deleteAll();
			while (rs.next()) {
				String id = rs.getString("ID");
				String name = rs.getString("NAME");
				this.repository.save(new Floor(id, name));
			}
			floors = repository.findAll().stream()
					.map(floor -> EntityModel.of(floor// ,
					// linkTo(methodOn(FloorController.class).one(floor.getId())).withSelfRel(),
					// linkTo(methodOn(FloorController.class).all()).withRel("floors")
					))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			e.printStackTrace();
			// TODO: handle exception
		}

		return CollectionModel.of(floors, linkTo(methodOn(FloorController.class).all()).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	@GetMapping("/floor/{id}")
	EntityModel<Floor> one(@PathVariable Long id) {

		Floor floor = repository.findById(id) //
				.orElseThrow(() -> new FloorNotFoundException(id));

		return EntityModel.of(floor, //
				linkTo(methodOn(FloorController.class).one(id)).withSelfRel(),
				linkTo(methodOn(FloorController.class).all()).withRel("floors"));
	}
	// end::get-single-item[]

	@PutMapping("/floor/{id}")
	Floor replaceFloor(@RequestBody Floor newFloor, @PathVariable Long id) {

		return repository.findById(id) //
				.map(floor -> {
					floor.setName(newFloor.getName());
					floor.setId(newFloor.getId());
					return repository.save(floor);
				}) //
				.orElseGet(() -> {
					return repository.save(newFloor);
				});
	}

	@DeleteMapping("/floor/{id}")
	void deleteFloor(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
