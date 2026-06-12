package w4cash.floors;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
class Floor {

	// id: row.ID, code:row.CODE, name: row.NAME, pricesell: row.PRICESELL,
	// category: row.CATEGORY
	private @Id @GeneratedValue Long id;
	private String id_;
	private String name;

	Floor() {
	}

	// id, code, name, pricesell, category
	Floor(String id_, String name) {
		this.id_ = id_;
		this.name = name;
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

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof Floor))
			return false;
		Floor floor = (Floor) o;
		return Objects.equals(this.id, floor.id) && Objects.equals(this.id_, floor.id_)
				&& Objects.equals(this.name, floor.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.id_, this.name);
	}

	@Override
	public String toString() {
		return "Floor {" + "id=" + this.id + ", id_='" + this.id_ + '\'' + ", name='" + this.name + '\'' + '}';
	}
}
