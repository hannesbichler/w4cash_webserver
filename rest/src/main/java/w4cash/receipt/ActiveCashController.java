package w4cash.receipt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import w4cash.LoadDatabase;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class ActiveCashController {
	private static final Logger logger = LoggerFactory.getLogger(ActiveCashController.class);

	private final ActiveCashRepository repository;

	ActiveCashController(ActiveCashRepository repository) {
		this.repository = repository;
	}

	// Aggregate root

	// tag::get-single-item[]
	@GetMapping("/activecash/{tabletId}/{openNew}/{ignoreCache}")
	CollectionModel<EntityModel<ActiveCash>> one(@PathVariable String tabletId, @PathVariable Boolean openNew,
			@PathVariable Boolean ignoreCache) {
		logger.info("GET /activecash request was called for tabletId={}, openNew={}, ignoreCache={}", tabletId, openNew,
				ignoreCache);
		ActiveCash activeCash = null;
		// One connection for the lookup and the conditional insert below.
		// NOTE: this still has a check-then-insert race -- two concurrent calls
		// with openNew=true can both find no open session and both insert, leaving
		// two CLOSEDCASH rows with DATEEND IS NULL for one host. Fixing that needs
		// a LOCK TABLE (as zReport does) or a unique constraint; out of scope for
		// this connection-pool change.
		try (Connection conn = LoadDatabase.getConnection()) {
			try (PreparedStatement st = conn.prepareStatement(
					"SELECT MONEY, HOST, HOSTSEQUENCE, DATESTART, DATEEND FROM CLOSEDCASH WHERE HOST = ? AND DATEEND IS NULL")) {
				st.setString(1, tabletId);
				try (ResultSet rs = st.executeQuery()) {
					while (rs.next()) {
						String money = rs.getString("MONEY");
						String host = rs.getString("HOST");
						String hostSequence = rs.getString("HOSTSEQUENCE");
						String dateStart = rs.getString("DATESTART");
						String dateEnd = rs.getString("DATEEND");
						activeCash = new ActiveCash(tabletId, money, host, hostSequence, dateStart, dateEnd);
						break;
					}
				}
			}

			if (activeCash == null && openNew) {
				logger.info("No activecash found for tabletId={}, openNew={}, ignoreCache={} create new one.", tabletId,
						openNew,
						ignoreCache);
				// create a new ActiveCash entry
				try (PreparedStatement st = conn.prepareStatement(
						"INSERT INTO CLOSEDCASH (MONEY, HOST, DATESTART, DATEEND, HOSTSEQUENCE, LOCATION) VALUES (?, ?, ?, ?, (SELECT NVL(max(hostsequence),0)+1 FROM CLOSEDCASH), '0') RETURNING HOSTSEQUENCE INTO ?")) {
					// pooled statement is a Hikari proxy; unwrap to reach Oracle-only
					// return-parameter API
					OraclePreparedStatement ost = st.unwrap(OraclePreparedStatement.class);
					var id = UUID.randomUUID().toString();
					ost.setString(1, id);
					ost.setString(2, tabletId);
					ost.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis())); // DATESTART
					ost.setString(4, null); // DATEEND
					ost.registerReturnParameter(5, OracleTypes.VARCHAR);
					ost.executeUpdate();
					try (ResultSet generatedKeys = ost.getReturnResultSet()) {
						if (generatedKeys.next()) {
							String hostSequence = generatedKeys.getString(1);
							activeCash = new ActiveCash(tabletId, id, tabletId, hostSequence, null, null);
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		List<EntityModel<ActiveCash>> result = new ArrayList<>();
		if (activeCash != null) {
			result.add(EntityModel.of(activeCash));
		}
		return CollectionModel.of(result);
	}
	// end::get-single-item[]
}
