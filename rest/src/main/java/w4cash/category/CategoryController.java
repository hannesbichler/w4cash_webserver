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
import org.springframework.web.util.HtmlUtils;

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
	@GetMapping("/categories")
	CollectionModel<EntityModel<Category>> all() {
		List<EntityModel<Category>> categories = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME, PARENTID FROM CATEGORIES");
				ResultSet rs = st.executeQuery()) {
			repository.deleteAll();
			while (rs.next()) {
				String id = rs.getString("ID");
				String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
				String parentId = rs.getString("PARENTID");
				var category = new Category(id, name, parentId);

				this.repository.findAll().stream().filter(c -> c.getId_().equals(parentId)).findFirst()
						.ifPresentOrElse(parent -> parent.getChildren().add(category), () -> {
							this.repository.save(category);
						});

			}

			categories = repository.findAll().stream()
					.map(category -> EntityModel.of(category// ,
					// linkTo(methodOn(CategoryController.class).one(category.getId())).withSelfRel(),
					// linkTo(methodOn(CategoryController.class).all()).withRel("categories")
					))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			// TODO: handle exception
		}

		return CollectionModel.of(categories, linkTo(methodOn(CategoryController.class).all()).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	@GetMapping("/category/{id}")
	EntityModel<Category> one(@PathVariable Long id) {

		Category category = repository.findById(id) //
				.orElseThrow(() -> new CategoryNotFoundException(id));

		return EntityModel.of(category, //
				linkTo(methodOn(CategoryController.class).one(id)).withSelfRel(),
				linkTo(methodOn(CategoryController.class).all()).withRel("categories"));
	}
	// end::get-single-item[]

	@PutMapping("/category/{id}")
	Category replaceCategory(@RequestBody Category newCategory, @PathVariable Long id) {

		return repository.findById(id) //
				.map(category -> {
					category.setName(newCategory.getName());
					return repository.save(category);
				}) //
				.orElseGet(() -> {
					return repository.save(newCategory);
				});
	}

	@DeleteMapping("/category/{id}")
	void deleteCategory(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
