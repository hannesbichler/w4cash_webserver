package w4cash;

import java.sql.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openbravo.pos.util.AltEncrypter;

@Configuration
public class LoadDatabase {

	public static Connection DBConnection = null;
	// private static final Logger log =
	// LoggerFactory.getLogger(LoadDatabase.class);

	@Bean
	CommandLineRunner initDatabase() {
		return args -> {
			try {
				var config = w4cash.W4cashApplication.APP_CONFIG;
				var oadriver = Class.forName(config.getProperty("db.driver")).getDeclaredConstructor().newInstance();
				DriverManager.registerDriver((Driver) oadriver);
				String url = config.getProperty("db.URL");
				String user = config.getProperty("db.user");
				String password = config.getProperty("db.password");
				if (user != null && password != null && password.startsWith("crypt:")) {
					// the password is encrypted
					AltEncrypter cypher = new AltEncrypter("cypherkey" + user);
					password = cypher.decrypt(password.substring(6));
				}
				DBConnection = DriverManager.getConnection(url, user, password);
			} catch (Exception e) {
				java.util.logging.Logger.getLogger(LoadDatabase.class.getName()).severe(e.getMessage());
			}
		};
	}
}
