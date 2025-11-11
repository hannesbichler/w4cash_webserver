package w4cash.product;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
class Product {

	// id: row.ID, code:row.CODE, name: row.NAME, pricesell: row.PRICESELL,
	// category: row.CATEGORY
	private @Id @GeneratedValue Long id;
	private String id_;
	private String code;
	private String name;
	private float pricesell;
	private String category;

	Product() {
	}

	// id, code, name, pricesell, category
	Product(String id_, String code, String name, float pricesell, String category) {
		this.id_ = id_;
		this.code = code;
		this.name = name;
		this.pricesell = pricesell;
		this.category = category;
	}

	public Long getId() {
		return this.id;
	}

	public String getId_() {
		return this.id_;
	}

	public String getCode() {
		return this.code;
	}

	public String getName() {
		return this.name;
	}

	public float getPricesell() {
		return this.pricesell;
	}

	public String getCategory() {
		return this.category;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setId_(String id_) {
		this.id_ = id_;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPricesell(float pricesell) {
		this.pricesell = pricesell;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof Product))
			return false;
		Product product = (Product) o;
		return Objects.equals(this.id, product.id) && Objects.equals(this.code, product.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.code);
	}

	@Override
	public String toString() {
		return "Product{" + "id=" + this.id + ", name='" + this.name + '\'' + ", category='" + this.category + '\''
				+ '}';
	}
}
