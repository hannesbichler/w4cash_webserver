package w4cash.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Transient;

@Entity
class Category {

    private @Id @GeneratedValue Long id;
    private String id_;
    private String name;
    private String parentId;
    private int printer = 1;
    @Transient
    private List<Category> children;

    public Category() {
    }

    public Category(String id_, String name, String parentId) {
        this.id_ = id_;
        this.name = name;
        this.parentId = parentId;
    }

    public Category(String id_, String name, String parentId, int printer) {
        this.id_ = id_;
        this.name = name;
        this.parentId = parentId;
        this.printer = printer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getId_() {
        return this.id_;
    }

    public void setId_(String id_) {
        this.id_ = id_;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getPrinter() {
        return printer;
    }

    public void setPrinter(int printer) {
        this.printer = printer;
    }

    public List<Category> getChildren() {
        if (this.children == null) {
            this.children = new ArrayList<Category>();
        }
        return children;
    }

    public void setChildren(List<Category> children) {
        this.children = children;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;
        if (!(o instanceof Category))
            return false;
        Category category = (Category) o;
        return Objects.equals(this.id, category.id) && Objects.equals(this.id_, category.id_)
                && Objects.equals(this.name, category.name) && Objects.equals(this.parentId, category.parentId)
                && this.printer == category.printer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.id_, this.name, this.parentId, this.printer);
    }

    @Override
    public String toString() {
        return "Category {" + "id=" + this.id + ", id_='" + this.id_ + '\'' + ", name='" + this.name + '\''
                + ", parentId='" + this.parentId + '\'' + ", printer=" + this.printer + '}';
    }
}
