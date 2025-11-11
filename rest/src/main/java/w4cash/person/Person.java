package w4cash.person;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
class Person {

	// id: row.ID, code:row.CODE, name: row.NAME, pricesell: row.PRICESELL,
	// category: row.CATEGORY
	private @Id @GeneratedValue Long id;
	private String id_;
	private String name;
	private String apppassword;
	private String card;
	private String role;
	private String image;

	Person() {
	}

	// id, code, name, pricesell, category
	Person(String id_, String name, String apppassword, String card, String role, String image) {
		this.id_ = id_;
		this.name = name;
		this.apppassword = apppassword;
		this.card = card;
		this.role = role;
		this.image = image;
	}

	public Long getId() {
		return this.id;
	}

	public String getId_() {
		return this.id_;
	}

	public String getName() {
		return this.name;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setId_(String id_) {
		this.id_ = id_;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getApppassword() {
		return this.apppassword;
	}

	public void setApppassword(String apppassword) {
		this.apppassword = apppassword;
	}

	public String getCard() {
		return this.card;
	}

	public void setCard(String card) {
		this.card = card;
	}

	public String getRole() {
		return this.role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getImage() {
		return this.image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof Person))
			return false;
		Person product = (Person) o;
		return Objects.equals(this.id, product.id) && Objects.equals(this.name, product.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.name);
	}

	@Override
	public String toString() {
		return "Product{" + "id=" + this.id + ", name='" + this.name + '\'' + ", role='" + this.role + '\''
				+ '}';
	}
}
