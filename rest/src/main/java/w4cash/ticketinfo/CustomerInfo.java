package w4cash.ticketinfo;

import java.io.Serializable;

/**
 *
 * @author adrianromero
 */
public class CustomerInfo implements Serializable {

    private static final long serialVersionUID = 9083257536541L;
    protected String id;
    protected String searchkey;
    protected String taxid;
    protected String name;
    protected String address;

    /** Creates a new instance of UserInfoBasic */
    public CustomerInfo(String id) {
        this.id = id;
        this.searchkey = null;
        this.taxid = null;
        this.name = null;
    }

    public String getId() {
        return id;
    }

    public String getTaxid() {
        return taxid;
    }

    public void setTaxid(String taxid) {
        this.taxid = taxid;
    }

    public String getSearchkey() {
        return searchkey;
    }

    public void setSearchkey(String searchkey) {
        this.searchkey = searchkey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String printTaxid() {
        return StringUtils.encodeXML(taxid);
    }

    public String printName() {
        return StringUtils.encodeXML(name);
    }

    @Override
    public String toString() {
        return getName();
    }
}