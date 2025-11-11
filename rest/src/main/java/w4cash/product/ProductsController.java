package w4cash.product;

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
class ProductsController {

	private final ProductsRepository repository;

	ProductsController(ProductsRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/products")
	CollectionModel<EntityModel<Product>> all() {
		List<EntityModel<Product>> products = new ArrayList<>();
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
				this.repository.save(new Product(id, code, name, pricesell, category));
			}
			products = repository.findAll().stream()
					.map(product -> EntityModel.of(product,
							linkTo(methodOn(ProductsController.class).one(product.getCode())).withSelfRel(),
							linkTo(methodOn(ProductsController.class).all()).withRel("product")))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			// TODO: handle exception
		}

		return CollectionModel.of(products, linkTo(methodOn(ProductsController.class).all()).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	// tag::get-single-item[]
	@GetMapping("/product/{code}")
	EntityModel<Product> one(@PathVariable String code) {

		Product product = repository.findById(code) //
				.orElseThrow(() -> new ProductNotFoundException(code));

		return EntityModel.of(product, //
				linkTo(methodOn(ProductsController.class).one(code)).withSelfRel(),
				linkTo(methodOn(ProductsController.class).all()).withRel("employees"));
	}
	// end::get-single-item[]

	@PutMapping("/employees/{code}")
	Product replaceEmployee(@RequestBody Product newEmployee, @PathVariable String code) {

		return repository.findById(code) //
				.map(employee -> {
					employee.setName(newEmployee.getName());
					employee.setCategory(newEmployee.getCategory());
					return repository.save(employee);
				}) //
				.orElseGet(() -> {
					return repository.save(newEmployee);
				});
	}

	@DeleteMapping("/employees/{code}")
	void deleteEmployee(@PathVariable String code) {
		repository.deleteById(code);
	}
}
