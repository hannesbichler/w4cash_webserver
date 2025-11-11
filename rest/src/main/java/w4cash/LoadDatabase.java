package w4cash;

import java.sql.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoadDatabase {

	public static Connection DBConnection = null;
	// private static final Logger log =
	// LoggerFactory.getLogger(LoadDatabase.class);

	@Bean
	CommandLineRunner initDatabase() {
		return args -> {
			try {
				Class.forName("oracle.jdbc.driver.OracleDriver");
				DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
				String url = "jdbc:oracle:thin:@localhost:1521:xe";
				String user = "w4cash";
				String password = "w4cash";
				DBConnection = DriverManager.getConnection(url, user, password);
			} catch (ClassNotFoundException | SQLException e) {
				java.util.logging.Logger.getLogger(LoadDatabase.class.getName()).severe(e.getMessage());
			}
		};
	}
}
