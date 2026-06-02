package w4cash.ticketinfo;

import jakarta.persistence.Embeddable;

@Embeddable
public class OrderLine {
    private String id;
    private String orderId;
    private String productId;
    private String productName;
    private double pricesell;
    private double qty;

    public OrderLine() {
    }

    public OrderLine(String id, String orderId, String productId, String productName, double pricesell, double qty) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.pricesell = pricesell;
        this.qty = qty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getPricesell() {
        return pricesell;
    }

    public void setPricesell(double pricesell) {
        this.pricesell = pricesell;
    }

    public double getQty() {
        return qty;
    }

    public void setQty(double qty) {
        this.qty = qty;
    }
}
