package w4cash.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Settings {
    private @Id @GeneratedValue Long id;
    String tabletId;
    int catHeight;
    int catWidth;
    int categoryImgWidth;
    int productImgWidth;

    public Settings() {
    }

    public Settings(String tabletId, int catHeight, int catWidth, int categoryImgWidth, int productImgWidth) {
        this.tabletId = tabletId;
        this.catHeight = catHeight;
        this.catWidth = catWidth;
        this.categoryImgWidth = categoryImgWidth;
        this.productImgWidth = productImgWidth;
    }

    public String getTabletId() {
        return tabletId;
    }

    public int getCatHeight() {
        return catHeight;
    }

    public int getCatWidth() {
        return catWidth;
    }

    public int getCategoryImgWidth() {
        return categoryImgWidth;
    }

    public int getProductImgWidth() {
        return productImgWidth;
    }

    public Long getId() {
        return id;
    }
}
