package w4cash.person;

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

import w4cash.person.Person;
import w4cash.person.PersonNotFoundException;
import w4cash.person.PersonRepository;
import w4cash.LoadDatabase;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class PersonController {

	private final PersonRepository repository;

	PersonController(PersonRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/persons")
	CollectionModel<EntityModel<Person>> all() {
		List<EntityModel<Person>> person = new ArrayList<>();
		try {

			PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement("SELECT ID, CODE, NAME, PRICESELL, CATEGORY FROM PRODUCTS");
			ResultSet rs = st.executeQuery();
			repository.deleteAll();
			while (rs.next()) {
				String id = rs.getString("ID");
				String code = rs.getString("CODE");
				String name = rs.getString("NAME");
				float pricesell = rs.getFloat("PRICESELL");
				String category = rs.getString("CATEGORY");
				this.repository.save(new Person(id, code, name, pricesell, category));
			}
			persons = repository.findAll().stream()
					.map(person -> EntityModel.of(person,
							linkTo(methodOn(PersonController.class).one(person.getCode())).withSelfRel(),
							linkTo(methodOn(PersonController.class).all()).withRel("person")))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			// TODO: handle exception
		}

		return CollectionModel.of(persons, linkTo(methodOn(PersonController.class).all()).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	@GetMapping("/persons/{code}")
	EntityModel<Person> one(@PathVariable String code) {

		Person person = repository.findById(code) //
				.orElseThrow(() -> new PersonNotFoundException(code));

		return EntityModel.of(person, //
				linkTo(methodOn(PersonController.class).one(code)).withSelfRel(),
				linkTo(methodOn(PersonController.class).all()).withRel("employees"));
	}
	// end::get-single-item[]

	@PutMapping("/persons/{code}")
	Person replaceEmployee(@RequestBody Person newPerson, @PathVariable String code) {

		return repository.findById(code) //
				.map(employee -> {
					employee.setName(newPerson.getName());
					employee.setCategory(newPerson.getCategory());
					return repository.save(person);
				}) //
				.orElseGet(() -> {
					return repository.save(newPerson);
				});
	}

	@DeleteMapping("/persons/{code}")
	void deleteEmployee(@PathVariable String code) {
		repository.deleteById(code);
	}
}
