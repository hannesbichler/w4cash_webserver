package w4cash.category;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

@RestController
class CategoryController {

	private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

	private final CategoriesRepository repository;

	CategoryController(CategoriesRepository repository) {
		this.repository = repository;
	}

	// Reads CATEGORIES straight through. This used to wipe and repopulate a JPA
	// mirror in in-memory H2 on every call, which meant two concurrent requests
	// could each observe the other's half-built table.
	@GetMapping("/categories")
	CollectionModel<EntityModel<Category>> all() {
		List<Category> roots = new ArrayList<>();

		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn
						.prepareStatement("SELECT ID, NAME, PARENTID, PRINTER FROM CATEGORIES");
				ResultSet rs = st.executeQuery()) {

			// Pass 1: materialise every row, keyed by id, preserving row order.
			Map<String, Category> byId = new LinkedHashMap<>();
			while (rs.next()) {
				String id = rs.getString("ID");
				byId.put(id, new Category(
						id,
						rs.getString("NAME"),
						rs.getString("PARENTID"),
						rs.getInt("PRINTER")));
			}

			// Pass 2: attach each category to its parent. Doing this only after
			// every row is known is what fixes the old single-pass behaviour,
			// where a category whose parent had not been read yet was silently
			// emitted as a top-level entry.
			for (Category category : byId.values()) {
				Category parent = byId.get(category.getParentId());
				if (parent == null || parent == category || createsCycle(byId, category)) {
					roots.add(category);
				} else {
					parent.getChildren().add(category);
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to load categories", e);
		}

		List<EntityModel<Category>> categories = roots.stream()
				.map(EntityModel::of)
				.collect(Collectors.toList());

		return CollectionModel.of(categories, linkTo(methodOn(CategoryController.class).all()).withSelfRel());
	}

	/**
	 * True when following PARENTID up from {@code category} comes back round to
	 * it. Cycles should not exist in CATEGORIES, but attaching one would build a
	 * cyclic object graph and send the JSON serialiser into infinite recursion,
	 * so such a row is treated as a root instead.
	 */
	private boolean createsCycle(Map<String, Category> byId, Category category) {
		for (Category ancestor = byId.get(category.getParentId()); ancestor != null; ancestor = byId
				.get(ancestor.getParentId())) {
			if (ancestor == category) {
				return true;
			}
		}
		return false;
	}
}
