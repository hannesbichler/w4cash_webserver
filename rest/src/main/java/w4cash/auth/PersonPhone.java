package w4cash.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PersonPhone {

    @Id
    private String phone;
    private String personId;

    public PersonPhone() {}

    public PersonPhone(String phone, String personId) {
        this.phone = phone;
        this.personId = personId;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }
}
