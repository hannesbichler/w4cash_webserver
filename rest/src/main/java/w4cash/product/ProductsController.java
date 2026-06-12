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
import org.springframework.web.util.HtmlUtils;

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
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement(
						"SELECT ID, CODE, NAME, PRICESELL, CATEGORY, ATTRIBUTESET_ID FROM PRODUCTS")) {
			try (ResultSet rs = st.executeQuery()) {
				repository.deleteAll();
				while (rs.next()) {
					String id = rs.getString("ID");
					String code = HtmlUtils.htmlEscape(rs.getString("CODE"));
					String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
					float pricesell = rs.getFloat("PRICESELL");
					String categoryId = rs.getString("CATEGORY");
					String attributeSetId = rs.getString("ATTRIBUTESET_ID");
					this.repository.save(new Product(id, code, name, pricesell, categoryId, attributeSetId));
				}
			}
			products = repository.findAll().stream()
					.map(product -> EntityModel.of(product))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			// TODO: handle exception
		}

		return CollectionModel.of(products, linkTo(methodOn(ProductsController.class).all()).withSelfRel());
	}

	// tag::get-aggregate-root[]
	@GetMapping("/products/{categoryId}")
	CollectionModel<EntityModel<Product>> all(@PathVariable String categoryId) {
		List<EntityModel<Product>> products = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement(
						"SELECT ID, CODE, NAME, PRICESELL, CATEGORY, ATTRIBUTESET_ID FROM PRODUCTS where CATEGORY = ?")) {
			st.setString(1, categoryId);
			try (ResultSet rs = st.executeQuery()) {
				repository.deleteAll();
				while (rs.next()) {
					String id = rs.getString("ID");
					String code = HtmlUtils.htmlEscape(rs.getString("CODE"));
					String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
					float pricesell = rs.getFloat("PRICESELL");
					String attributeSetId = rs.getString("ATTRIBUTESET_ID");
					this.repository.save(new Product(id, code, name, pricesell, categoryId, attributeSetId));
				}
			}
			products = repository.findAll().stream()
					.map(product -> EntityModel.of(product))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			// TODO: handle exception
		}

		return CollectionModel.of(products, linkTo(methodOn(ProductsController.class).all(categoryId)).withSelfRel());
	}

	// tag::get-single-item[]
	@GetMapping("/product/{id}")
	EntityModel<Product> one(@PathVariable Long id) {

		Product product = repository.findById(id) //
				.orElseThrow(() -> new ProductNotFoundException(id));

		return EntityModel.of(product);
	}
	// end::get-single-item[]

	@PutMapping("/product/{id}")
	Product replaceEmployee(@RequestBody Product newProduct, @PathVariable Long id) {

		return repository.findById(id) //
				.map(product -> {
					product.setName(newProduct.getName());
					product.setCategoryId(newProduct.getCategoryId());
					return repository.save(product);
				}) //
				.orElseGet(() -> {
					return repository.save(newProduct);
				});
	}

	@DeleteMapping("/products/{id}")
	void deleteProduct(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
