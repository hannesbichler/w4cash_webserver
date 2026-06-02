package w4cash.ticketinfo;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
class OrderItem {

    // id: row.ID, code:row.CODE, name: row.NAME, pricesell: row.PRICESELL,
    // category: row.CATEGORY
    private @Id @GeneratedValue Long id;
    private String id_ = "";
    private int tickettype = 0;
    private int m_iTicketId = 0;
    @ElementCollection(fetch = FetchType.EAGER)
    private List<OrderLine> m_aLines = new java.util.ArrayList<>();

    public String getId_() {
        return id_;
    }

    public void setId_(String id_) {
        this.id_ = id_;
    }

    public int getTickettype() {
        return tickettype;
    }

    public void setTickettype(int tickettype) {
        this.tickettype = tickettype;
    }

    public int getTicketId() {
        return m_iTicketId;
    }

    public void setTicketId(int ticketId) {
        this.m_iTicketId = ticketId;
    }

    public List<OrderLine> getLines() {
        return m_aLines;
    }

    public void setLines(List<OrderLine> lines) {
        this.m_aLines = lines;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;
        if (!(o instanceof OrderItem))
            return false;
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(this.id, orderItem.id) && Objects.equals(this.id_, orderItem.id_)
                && Objects.equals(this.tickettype, orderItem.tickettype)
                && Objects.equals(this.m_iTicketId, orderItem.m_iTicketId)
                && Objects.equals(this.m_aLines, orderItem.m_aLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.id_, this.tickettype, this.m_iTicketId, this.m_aLines);
    }

    @Override
    public String toString() {
        return "OrderItem{id:" + this.id + ",id_:" + this.id_ + ", tickettype:" + this.tickettype + ", ticketId:"
                + this.m_iTicketId + ", lines:" + this.m_aLines + "}";
    }
}
