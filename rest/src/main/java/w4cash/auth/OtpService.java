package w4cash.auth;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final Random RANDOM = new Random();
    private final Map<String, String> store = new ConcurrentHashMap<>();

    public String generate(String phone) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(normalize(phone), otp);
        // TODO: replace log with SMS gateway (e.g. Twilio, AWS SNS)
        logger.info("OTP for {}: {}", phone, otp);
        return otp;
    }

    public boolean verify(String phone, String otp) {
        String key = normalize(phone);
        String stored = store.get(key);
        if (stored != null && stored.equals(otp.trim())) {
            store.remove(key);
            return true;
        }
        return false;
    }

    private String normalize(String phone) {
        return phone.replaceAll("[^+\\d]", "");
    }
}
