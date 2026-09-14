package w4cash.product;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductsController {

	private static final Logger logger = LoggerFactory.getLogger(ProductsController.class);
	private final ProductsRepository repository;

	ProductsController(ProductsRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/products")
	ResponseEntity<?> all(@RequestParam(required = false) String categoryId) {
		logger.info("GET /products request was called, categoryId={}", categoryId);
		try {
			List<EntityModel<Product>> products = repository.findAll(categoryId).stream()
					.map(EntityModel::of)
					.collect(Collectors.toList());
			return ResponseEntity.ok(
					CollectionModel.of(products,
							linkTo(methodOn(ProductsController.class).all(categoryId)).withSelfRel()));
		} catch (SQLException e) {
			logger.error("Failed to load products, categoryId={}", categoryId, e);
			return ResponseEntity.internalServerError().body("Failed to load products");
		}
	}

	@GetMapping("/products/{id}")
	ResponseEntity<?> one(@PathVariable String id) {
		try {
			return repository.findById(id)
					.<ResponseEntity<?>>map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("No product with id=" + id));
		} catch (SQLException e) {
			logger.error("Failed to load product id={}", id, e);
			return ResponseEntity.internalServerError().body("Failed to load product");
		}
	}
}
