package w4cash.category;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;

@Entity
class Category {

    private @Id @GeneratedValue Long id;

    private String name;
    private Long parentId;
    private String image;
    private String bgcolor;
    private Integer sortOrder;
    private String printer;

    public Category() {
    }

    public Category(String name, Long parentId, String image, String bgcolor, Integer sortOrder, String printer) {
        this.name = name;
        this.parentId = parentId;
        this.image = image;
        this.bgcolor = bgcolor;
        this.sortOrder = sortOrder;
        this.printer = printer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getBgcolor() {
        return bgcolor;
    }

    public void setBgcolor(String bgcolor) {
        this.bgcolor = bgcolor;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPrinter() {
        return printer;
    }

    public void setPrinter(String printer) {
        this.printer = printer;
    }

}
