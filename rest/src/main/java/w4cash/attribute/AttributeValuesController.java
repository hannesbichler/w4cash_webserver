package w4cash.attribute;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Allowed values for an attribute (ATTRIBUTEVALUE), e.g. attribute "Size" -> values S, M, L, in order.
@RestController
class AttributeValuesController {

	private static final Logger logger = LoggerFactory.getLogger(AttributeValuesController.class);
	private final AttributeValuesRepository repository;
	private final AttributesRepository attributesRepository;

	AttributeValuesController(AttributeValuesRepository repository, AttributesRepository attributesRepository) {
		this.repository = repository;
		this.attributesRepository = attributesRepository;
	}

	@GetMapping("/attributes/{attributeId}/values")
	ResponseEntity<?> all(@PathVariable String attributeId) {
		try {
			if (attributesRepository.findById(attributeId).isEmpty()) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No attribute with id=" + attributeId);
			}
			return ResponseEntity.ok(repository.findAllForAttribute(attributeId));
		} catch (SQLException e) {
			logger.error("Failed to load values for attribute {}", attributeId, e);
			return ResponseEntity.internalServerError().body("Failed to load attribute values");
		}
	}

}
