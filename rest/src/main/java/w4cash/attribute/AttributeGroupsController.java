package w4cash.attribute;

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
class AttributeGroupsController {

	private final AttributeGroupsRepository repository;

	AttributeGroupsController(AttributeGroupsRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/attribute-groups/{setId}")
	CollectionModel<EntityModel<AttributeGroup>> all(@PathVariable String setId) {

		List<EntityModel<AttributeGroup>> attributeGroups = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement(
						"SELECT a.id, a.name FROM attributeuse au JOIN attribute a ON a.id = au.attribute_id WHERE au.attributeset_id = ? ORDER BY au.lineno, a.name")) {
			st.setString(1, setId);
			try (ResultSet rs = st.executeQuery()) {
				repository.deleteAll();
				while (rs.next()) {
					String id = rs.getString("ID");
					String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
					this.repository.save(new AttributeGroup(id, name));
				}
			}

		} catch (SQLException e) {
			// TODO: handle exception
		}
		for (AttributeGroup attributeGroup : repository.findAll()) {
			try (PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement(
							"SELECT av.id, av.value FROM attributeuse au JOIN attributevalue av ON av.attribute_id = au.attribute_id WHERE au.attributeset_id = ? and av.attribute_id = ? ORDER BY av.lineno, av.value")) {
				st.setString(1, setId);
				st.setString(2, attributeGroup.getAttributeGroupId());
				try (ResultSet rs = st.executeQuery()) {
					// repository.deleteAll();
					while (rs.next()) {
						String id = rs.getString("ID");
						String name = HtmlUtils.htmlEscape(rs.getString("VALUE"));

						attributeGroup.getAttributes().add(new Attribute(id, name));
						// this.repository.save(new AttributeGroup(id, name));
					}
				}

			} catch (SQLException e) {
				e.printStackTrace();
			}

			System.out.println(attributeGroup.toString());
		}

		attributeGroups = repository.findAll().stream()
				.map(attributeGroup -> EntityModel.of(attributeGroup))
				.collect(Collectors.toList());
		return CollectionModel.of(attributeGroups);
	}

	@PutMapping("/attribute-groups/{id}")
	AttributeGroup replaceAttributeGroup(@RequestBody AttributeGroup newAttributeGroup, @PathVariable Long id) {

		return repository.findById(id) //
				.map(attributeGroup -> {
					attributeGroup.setName(newAttributeGroup.getName());
					return repository.save(attributeGroup);
				}) //
				.orElseGet(() -> {
					return repository.save(newAttributeGroup);
				});
	}

	@DeleteMapping("/attribute-groups/{id}")
	void deleteAttributeGroup(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
