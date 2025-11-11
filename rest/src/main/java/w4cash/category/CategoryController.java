package w4cash.category;

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
class CategoryController {

	private final CategoryRepository repository;

	CategoryController(CategoryRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	/*
	 * @GetMapping("/categories")
	 * CollectionModel<EntityModel<Category>> all() {
	 * List<EntityModel<Category>> category = new ArrayList<>();
	 * try {
	 * 
	 * PreparedStatement st = LoadDatabase.DBConnection
	 * .prepareStatement("SELECT ID, CODE, NAME, PRICESELL, CATEGORY FROM PRODUCTS"
	 * );
	 * ResultSet rs = st.executeQuery();
	 * repository.deleteAll();
	 * while (rs.next()) {
	 * String id = rs.getString("ID");
	 * String code = rs.getString("CODE");
	 * String name = rs.getString("NAME");
	 * float pricesell = rs.getFloat("PRICESELL");
	 * String category = rs.getString("CATEGORY");
	 * this.repository.save(new Category(id, code, name, pricesell, category));
	 * }
	 * categories = repository.findAll().stream()
	 * .map(category -> EntityModel.of(category,
	 * linkTo(methodOn(CategoryController.class).one(person.getCode())).withSelfRel(
	 * ),
	 * linkTo(methodOn(CategoryController.class).all()).withRel("person")))
	 * .collect(Collectors.toList());
	 * } catch (SQLException e) {
	 * // TODO: handle exception
	 * }
	 * 
	 * return CollectionModel.of(categories,
	 * linkTo(methodOn(CategoryController.class).all()).withSelfRel());
	 * }
	 */
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	/*
	 * @GetMapping("/category/{code}")
	 * EntityModel<Category> one(@PathVariable String code) {
	 * 
	 * Category category = repository.findById(code) //
	 * .orElseThrow(() -> new CategoryNotFoundException(code));
	 * 
	 * return EntityModel.of(category, //
	 * linkTo(methodOn(CategoryController.class).one(code)).withSelfRel(),
	 * linkTo(methodOn(CategoryController.class).all()).withRel("employees"));
	 * }
	 */
	// end::get-single-item[]
	/*
	 * @PutMapping("/category/{code}")
	 * Category replaceEmployee(@RequestBody Category newCategory, @PathVariable
	 * String code) {
	 * 
	 * return repository.findById(code) //
	 * .map(category -> {
	 * employee.setName(newPerson.getName());
	 * employee.setCategory(newPerson.getCategory());
	 * return repository.save(person);
	 * }) //
	 * .orElseGet(() -> {
	 * return repository.save(newPerson);
	 * });
	 * }
	 */

	@DeleteMapping("/categories/{code}")
	void deleteEmployee(@PathVariable String code) {
		repository.deleteById(code);
	}
}
