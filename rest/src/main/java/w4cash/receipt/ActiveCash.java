package w4cash.receipt;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class ActiveCash {
    private @Id @GeneratedValue Long id;
    String m_sId;
    String m_money;
    String m_host;
    String m_hostSequence;
    String m_dateStart;
    String m_dateEnd;

    public ActiveCash() {
    }

    public ActiveCash(String sId, String money, String host, String hostSequence, String dateStart, String dateEnd) {
        this.m_sId = sId;
        this.m_money = money;
        this.m_host = host;
        this.m_hostSequence = hostSequence;
        this.m_dateStart = dateStart;
        this.m_dateEnd = dateEnd;
    }

    public Long getId() {
        return id;
    }

    public String getSId() {
        return m_sId;
    }

    public String getMoney() {
        return m_money;
    }

    public String getHost() {
        return m_host;
    }

    public String getHostSequence() {
        return m_hostSequence;
    }

    public String getDateStart() {
        return m_dateStart;
    }

    public String getDateEnd() {
        return m_dateEnd;
    }
}
