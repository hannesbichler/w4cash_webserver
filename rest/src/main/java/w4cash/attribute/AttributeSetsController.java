package w4cash.attribute;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Attribute sets (ATTRIBUTESET) and which attributes belong to each, in order (ATTRIBUTEUSE).
// Used to populate a product's "attribute set" picker and to let admins define what a set contains.
@RestController
class AttributeSetsController {

	private static final Logger logger = LoggerFactory.getLogger(AttributeSetsController.class);
	private final AttributeSetsRepository repository;

	AttributeSetsController(AttributeSetsRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/attribute-sets/{id}")
	ResponseEntity<?> one(@PathVariable String id) {
		try {
			return repository.findById(id)
					.<ResponseEntity<?>>map(ResponseEntity::ok)
					.orElseGet(
							() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("No attribute set with id=" + id));
		} catch (SQLException e) {
			logger.error("Failed to load attribute set id={}", id, e);
			return ResponseEntity.internalServerError().body("Failed to load attribute set");
		}
	}

}
