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
		List<EntityModel<Person>> persons = new ArrayList<>();
		try {

			PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement("SELECT ID, NAME, APPPASSWORD, CARD, ROLE, IMAGE FROM PEOPLE");
			ResultSet rs = st.executeQuery();
			repository.deleteAll();
			while (rs.next()) {
				String id = rs.getString("ID");
				String name = rs.getString("NAME");
				String apppassword = rs.getString("APPPASSWORD");
				String card = rs.getString("CARD");
				String role = rs.getString("ROLE");
				String image = rs.getString("IMAGE");
				this.repository.save(new Person(id, name, apppassword, card, role, image));
			}
			persons = repository.findAll().stream()
					.map(person -> EntityModel.of(person,
							linkTo(methodOn(PersonController.class).one(person.getId_())).withSelfRel(),
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
	@GetMapping("/persons/{id}")
	EntityModel<Person> one(@PathVariable String id_) {

		Person person = repository.findById(id_) //
				.orElseThrow(() -> new PersonNotFoundException(id_));

		return EntityModel.of(person, //
				linkTo(methodOn(PersonController.class).one(id_)).withSelfRel(),
				linkTo(methodOn(PersonController.class).all()).withRel("employees"));
	}
	// end::get-single-item[]

	@PutMapping("/persons/{id}")
	Person replacePerson(@RequestBody Person newPerson, @PathVariable String id_) {

		return repository.findById(id_) //
				.map(person -> {
					person.setName(newPerson.getName());
					person.setApppassword(newPerson.getApppassword());
					return repository.save(person);
				}) //
				.orElseGet(() -> {
					return repository.save(newPerson);
				});
	}

	@DeleteMapping("/persons/{id}")
	void deletePerson(@PathVariable String id_) {
		repository.deleteById(id_);
	}
}
