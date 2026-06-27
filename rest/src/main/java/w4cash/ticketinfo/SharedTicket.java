package w4cash.ticketinfo;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;

@Entity
class SharedTicket {

    private @Id @GeneratedValue Long id;
    private String m_sId;
    private String m_name;
    private byte[] m_content;
    private String m_lockby;

    public SharedTicket() {
    }

    public SharedTicket(String id_, String name) {
        this.m_sId = id_;
        this.m_name = name;
    }

    public SharedTicket(String id_, String name, byte[] content, String lockby) {
        this.m_sId = id_;
        this.m_name = name;
        this.m_content = content;
        this.m_lockby = lockby;
    }

    public Long getId() {
        return id;
    }

    public String getSId() {
        return m_sId;
    }

    public String getName() {
        return m_name;
    }

    public byte[] getContent() {
        return m_content;
    }

    public String getLockby() {
        return m_lockby;
    }

    public void setLockby(String lockby) {
        this.m_lockby = lockby;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_sId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SharedTicket that = (SharedTicket) obj;
        return Objects.equals(m_sId, that.m_sId);
    }

}