package w4cash.attribute;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
class AttributeGroup {

	// id: row.ID, code:row.CODE, name: row.NAME, pricesell: row.PRICESELL,
	// category: row.CATEGORY
	private @Id @GeneratedValue Long id;
	private String attributeGroupId;
	private String name;
	@ElementCollection(fetch = FetchType.EAGER)
	private List<Attribute> attributes = new java.util.ArrayList<>();

	AttributeGroup() {
	}

	// id, code, name, pricesell, category, attributeGroupId
	AttributeGroup(String attributeGroupId, String name) {
		this.attributeGroupId = attributeGroupId;
		this.name = name;
	}

	public Long getId() {
		return this.id;
	}

	public String getAttributeGroupId() {
		return this.attributeGroupId;
	}

	public String getName() {
		return this.name;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAttributeGroupId(String attributeGroupId) {
		this.attributeGroupId = attributeGroupId;
	}

	public List<Attribute> getAttributes() {
		return this.attributes;
	}

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof AttributeGroup))
			return false;
		AttributeGroup attributeGroup = (AttributeGroup) o;
		return Objects.equals(this.id, attributeGroup.id)
				&& Objects.equals(this.attributeGroupId, attributeGroup.attributeGroupId)
				&& Objects.equals(this.name, attributeGroup.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.attributeGroupId, this.name);
	}

	@Override
	public String toString() {
		return "AttributeSet{id:" + this.id + ", name:'" + this.name + "'" + ", attributeSetId:'"
				+ this.attributeGroupId
				+ "'"
				+ '}';
	}
}
