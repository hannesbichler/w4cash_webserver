package w4cash.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import w4cash.LoadDatabase;

@RestController
@RequestMapping("/auth/otp")
public class OtpController {

    private static final Logger logger = LoggerFactory.getLogger(OtpController.class);

    private final OtpService otpService;

    OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSamplePhone() {
        try (Connection conn = LoadDatabase.getConnection();
        		PreparedStatement st = conn.prepareStatement(
                "SELECT NAME, CARD FROM PEOPLE WHERE CARD IS NOT NULL AND CARD <> ''");
                ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                logger.info("Sample phone for OTP testing — user: '{}', card/phone: '{}'",
                        rs.getString("NAME"), rs.getString("CARD"));
            }
        } catch (Exception e) {
            logger.warn("Could not read sample phone: {}", e.getMessage());
        }
    }

    /**
     * Send OTP to the given phone number. Returns 404 if no person has that card
     * value.
     */
    @PostMapping("/send")
    ResponseEntity<Map<String, String>> send(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone required"));
        }
        if (findPersonByCard(normalize(phone)).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "no user found for this number"));
        }
        otpService.generate(phone);
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    /** Verify OTP and return the matching person. */
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
        return findPersonByCard(normalize(phone))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "user not found")));
    }

    private Optional<Map<String, String>> findPersonByCard(String card) {
        try (Connection conn = LoadDatabase.getConnection();
        		PreparedStatement st = conn.prepareStatement(
                "SELECT ID, NAME, APPPASSWORD, CARD, ROLE FROM PEOPLE WHERE CARD = ?")) {
            st.setString(1, card);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(Map.of(
                            "id_", rs.getString("ID"),
                            "name", rs.getString("NAME"),
                            "apppassword", rs.getString("APPPASSWORD") != null ? rs.getString("APPPASSWORD") : "",
                            "card", rs.getString("CARD") != null ? rs.getString("CARD") : "",
                            "role", rs.getString("ROLE") != null ? rs.getString("ROLE") : ""));
                }
            }
        } catch (Exception e) {
            logger.warn("DB error looking up card '{}': {}", card, e.getMessage());
        }
        return Optional.empty();
    }

    private String normalize(String phone) {
        return phone.replaceAll("[^+\\d]", "");
    }
}
