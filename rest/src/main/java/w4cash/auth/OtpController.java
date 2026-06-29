package w4cash.auth;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

@RestController
@RequestMapping("/auth/otp")
public class OtpController {

    private final OtpService otpService;
    private final PersonPhoneRepository personPhoneRepository;

    OtpController(OtpService otpService, PersonPhoneRepository personPhoneRepository) {
        this.otpService = otpService;
        this.personPhoneRepository = personPhoneRepository;
    }

    /** Send OTP to the given phone number. Returns 404 if no user is registered for that number. */
    @PostMapping("/send")
    ResponseEntity<Map<String, String>> send(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone required"));
        }
        if (personPhoneRepository.findByPhone(normalize(phone)).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "no user registered for this number"));
        }
        otpService.generate(phone);
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    /** Verify OTP and return the matching person's id_ and name. */
    @PostMapping("/verify")
    ResponseEntity<Map<String, String>> verify(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String otp = body.get("otp");
        if (phone == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone and otp required"));
        }
        if (!otpService.verify(phone, otp)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid otp"));
        }
        return personPhoneRepository.findByPhone(normalize(phone))
                .map(pp -> queryPerson(pp.getPersonId()))
                .map(p -> ResponseEntity.ok(p))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "user not found")));
    }

    /** Register (or update) the phone number for a person after they have already logged in with password. */
    @PostMapping("/register")
    ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String personId = body.get("personId");
        String phone = body.get("phone");
        if (personId == null || phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "personId and phone required"));
        }
        personPhoneRepository.save(new PersonPhone(normalize(phone), personId));
        return ResponseEntity.ok(Map.of("status", "registered"));
    }

    private Map<String, String> queryPerson(String personId) {
        try (PreparedStatement st = LoadDatabase.DBConnection.prepareStatement(
                "SELECT ID, NAME, APPPASSWORD, CARD, ROLE FROM PEOPLE WHERE ID = ?")) {
            st.setString(1, personId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Map.of(
                            "id_", rs.getString("ID"),
                            "name", rs.getString("NAME"),
                            "apppassword", rs.getString("APPPASSWORD") != null ? rs.getString("APPPASSWORD") : "",
                            "card", rs.getString("CARD") != null ? rs.getString("CARD") : "",
                            "role", rs.getString("ROLE") != null ? rs.getString("ROLE") : "");
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return Map.of();
    }

    private String normalize(String phone) {
        return phone.replaceAll("[^+\\d]", "");
    }
}
