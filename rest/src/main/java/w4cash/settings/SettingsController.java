package w4cash.settings;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import w4cash.LoadDatabase;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class SettingsController {
	private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

	private final SettingsRepository repository;

	SettingsController(SettingsRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-single-item[]
	@GetMapping("/tablets")
	CollectionModel<EntityModel<Settings>> allTablets() {
		logger.info("GET /tablets request was called");
		List<EntityModel<Settings>> settingsList = new ArrayList<>();
		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn
				.prepareStatement(
						"SELECT MONEY, HOST, HOSTSEQUENCE, DATESTART, DATEEND FROM CLOSEDCASH")) {
			// st.setString(1, tabletId);
			try (ResultSet rs = st.executeQuery()) {
				var index = 1;
				settingsList.add(EntityModel.of(new Settings("Tablet1", 100, 200, 300, 400)));
				settingsList.add(EntityModel.of(new Settings("Tablet2", 100, 200, 300, 400)));
				settingsList.add(EntityModel.of(new Settings("Tablet3", 100, 200, 300, 400)));
				settingsList.add(EntityModel.of(new Settings("Tablet4", 100, 200, 300, 400)));
				settingsList.add(EntityModel.of(new Settings("Tablet5", 100, 200, 300, 400)));
				settingsList.add(EntityModel.of(new Settings("Tablet6", 100, 200, 300, 400)));
				while (rs.next()) {
					settingsList.add(EntityModel.of(new Settings("Tablet" + index, 100, 200, 300, 400)));
					index++;
					break;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return CollectionModel.of(settingsList);
	}

	@GetMapping("/settings/{tabletId}")
	CollectionModel<EntityModel<Settings>> all(@PathVariable String tabletId) {
		logger.info("GET /settings request was called for tabletId={}", tabletId);
		List<EntityModel<Settings>> settingsList = new ArrayList<>();
		try (Connection conn = LoadDatabase.getConnection();
				PreparedStatement st = conn
				.prepareStatement(
						"SELECT CONTENT from RESOURCES where NAME=?")) {
			st.setString(1, tabletId + "/properties");
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					Properties p = new Properties();
					p.loadFromXML(rs.getBinaryStream("CONTENT"));
					var catheight = p.getProperty("cat-height");
					var catwidth = p.getProperty("cat-width");
					var categoryImgWidth = p.getProperty("category-img-width");
					var productImgWidth = p.getProperty("product-img-width");
					if (catheight == null || catheight.isEmpty())
						catheight = "100";
					if (catwidth == null || catwidth.isEmpty())
						catwidth = "200";
					if (categoryImgWidth == null || categoryImgWidth.isEmpty())
						categoryImgWidth = "300";
					if (productImgWidth == null || productImgWidth.isEmpty())
						productImgWidth = "400";
					settingsList.add(EntityModel.of(new Settings(tabletId,
							Integer.parseInt(catheight),
							Integer.parseInt(catwidth),
							Integer.parseInt(categoryImgWidth),
							Integer.parseInt(productImgWidth))));
					break;
				}
			} catch (InvalidPropertiesFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return CollectionModel.of(settingsList);
	}
	// end::get-single-item[]
}
