package w4cash.ticketinfo;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;

import w4cash.LoadDatabase;
import w4cash.attribute.Attribute;
import w4cash.print.TicketPrintService;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class TicketInfoController {
	private static final Logger logger = LoggerFactory.getLogger(TicketInfoController.class);

	private final SharedTicketRepository repository;
	private final TicketPrintService ticketPrintService;

	TicketInfoController(SharedTicketRepository repository, TicketPrintService ticketPrintService) {
		this.repository = repository;
		this.ticketPrintService = ticketPrintService;
	}

	// Aggregate root

	// tag::get-aggregate-root[]
	@GetMapping("/TicketInfos")
	CollectionModel<EntityModel<SharedTicket>> all() {
		List<EntityModel<SharedTicket>> sharedTickets = new ArrayList<>();
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME FROM SHAREDTICKETS");
				ResultSet rs = st.executeQuery()) {
			repository.deleteAll();
			while (rs.next()) {
				String id = rs.getString("ID");
				String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
				this.repository.save(new SharedTicket(id, name));
			}
			sharedTickets = repository.findAll().stream()
					.map(sharedTicket -> EntityModel.of(sharedTicket// ,
					// linkTo(methodOn(TicketInfoController.class).one(sharedTicket.getId())).withSelfRel(),
					// linkTo(methodOn(TicketInfoController.class).all()).withRel("sharedTickets")
					))
					.collect(Collectors.toList());
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return CollectionModel.of(sharedTickets, linkTo(methodOn(TicketInfoController.class).all()).withSelfRel());
	}
	// end::get-aggregate-root[]

	// @PostMapping("/employees")
	// Product newEmployee(@RequestBody Product newEmployee) {
	// return repository.save(newEmployee);
	// }

	// Single item

	private List<Attribute> parseAttributes(String productAttSetInstDesc) {
		List<Attribute> attributes = new ArrayList<>();
		if (productAttSetInstDesc == null || productAttSetInstDesc.isBlank()) {
			return attributes;
		}

		String[] tokens = productAttSetInstDesc.replace("\r", "\n").split("\\n|;|,");
		for (String name : tokens) {
			String entry = name.trim();
			String id = "";
			if (entry.isEmpty()) {
				continue;
			}

			try (PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement("SELECT ID, value FROM ATTRIBUTEVALUE where value = ?")) {
				st.setString(1, entry);
				ResultSet rs = st.executeQuery();

				while (rs.next()) {
					id = rs.getString("ID");
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			attributes.add(new Attribute(HtmlUtils.htmlEscape(id), HtmlUtils.htmlEscape(name)));
		}

		return attributes;

	}

	private byte[] encodeContent(TicketInfo ticketInfo) {
		if (ticketInfo == null) {
			logger.warn("encodeContent called with null TicketInfo");
			return new byte[0];
		}

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
			objectOut.writeObject(ticketInfo);
			objectOut.flush();
			return byteOut.toByteArray();
		} catch (IOException ex) {
			logger.error("Failed to serialize TicketInfo", ex);
			return new byte[0];
		}
	}

	private OrderItem decodeContent(byte[] content) {
		OrderItem orderItem = new OrderItem();
		if (content == null || content.length == 0) {
			logger.warn("SHAREDTICKETS.CONTENT is null/empty");
			return orderItem;
		}

		TicketInfo ticketInfo = null;
		Exception readObjectException = null;

		// Preferred format: full Java object stream for TicketInfo.
		try {
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(content)) {
				@Override
				protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
					Class<?> registered = TicketInfo.class.getName().equals(desc.getName()) ? TicketInfo.class
							: null;
					if (registered != null) {
						return registered;
					}

					try {
						return Class.forName(desc.getName(), false, TicketInfoController.class.getClassLoader());
					} catch (ClassNotFoundException ex) {
						return super.resolveClass(desc);
					}
				}
			};
			ticketInfo = (TicketInfo) in.readObject();
			in.close();
		} catch (IOException | ClassNotFoundException ex) {
			readObjectException = ex;
		}

		if (ticketInfo == null) {
			logger.error(
					"Failed to deserialize SHAREDTICKETS.CONTENT. length={}, hexPrefix={}, base64Prefix={}",
					content.length,
					hexPrefix(content, 16),
					base64Prefix(content, 48));
			if (readObjectException != null) {
				logger.error("readObject failed", readObjectException);
			}
			return orderItem;
		}

		ticketInfo.getLines().forEach(line -> {
			String attSetInstDesc = line.getProductAttSetInstDesc();
			List<Attribute> attributes = parseAttributes(attSetInstDesc);
			var productName = line.getProductName();
			if (productName != null) {
				productName = HtmlUtils.htmlEscape(productName);
			}
			orderItem.getLines().add(new OrderLine(
					"", "", line.getProductID(), productName, line.getPrice(),
					line.getMultiply(), 0, line.getProductAttSetId(),
					HtmlUtils.htmlEscape(attSetInstDesc), attributes));
		});
		return orderItem;
	}

	private String hexPrefix(byte[] data, int bytes) {
		int len = Math.min(data.length, bytes);
		StringBuilder sb = new StringBuilder(len * 2);
		for (int i = 0; i < len; i++) {
			sb.append(String.format("%02X", data[i]));
		}
		return sb.toString();
	}

	private String base64Prefix(byte[] data, int bytes) {
		int len = Math.min(data.length, bytes);
		byte[] prefix = new byte[len];
		System.arraycopy(data, 0, prefix, 0, len);
		return Base64.getEncoder().encodeToString(prefix);
	}

	// tag::get-single-item[]
	@GetMapping("/orderitem/{tableId}/{tableName}/{lockby}")
	EntityModel<OrderItem> one(@PathVariable String tableId, @PathVariable String tableName,
			@PathVariable String lockby) {
		logger.info("GET /orderitem request was called for tableId={}, tableName={}, lockby={}", tableId, tableName,
				lockby);
		SharedTicket sharedTicket = null;
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME, CONTENT, LOCKBY FROM SHAREDTICKETS where ID = ?")) {
			st.setString(1, tableId);
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					String id_ = rs.getString("ID");
					String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
					byte[] content = rs.getBytes("CONTENT");
					String lockbyfrom = rs.getString("LOCKBY");
					// TODO if lockbyfrom is not null and not equal to lockby, then return error or
					// empty orderitem
					sharedTicket = new SharedTicket(id_, name, content, lockbyfrom);
					break;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		OrderItem orderitem = null;
		if (sharedTicket != null) {
			orderitem = decodeContent(sharedTicket.getContent());
			orderitem.setId_(sharedTicket.getSId());
			orderitem.setLockby(sharedTicket.getLockby());
			// update lockby in sharedtickets table if it is null or different from the
			// current lockby
			if (sharedTicket.getLockby() == null || !sharedTicket.getLockby().equals(lockby)) {
				try (PreparedStatement updateSt = LoadDatabase.DBConnection
						.prepareStatement("UPDATE SHAREDTICKETS SET LOCKBY = ? where ID = ?")) {
					updateSt.setString(1, lockby);
					updateSt.setString(2, tableId);
					updateSt.executeUpdate();
					sharedTicket.setLockby(lockby);
				} catch (SQLException e) {
					logger.error("Failed to update SHAREDTICKETS.LOCKBY for tableId={}", tableId, e);
				}
			}
			// orderitem.setTickettype(sharedTicket.getTickettype());
		} else {
			orderitem = new OrderItem();
			orderitem.setId_(tableId);
			orderitem.setLockby(lockby);
			orderitem.setKellner(lockby);

			var ticketinfo = new TicketInfo();
			byte[] content = encodeContent(ticketinfo);
			// Add a new empty shared ticket row for this table on first access.
			try (PreparedStatement insertSt = LoadDatabase.DBConnection
					.prepareStatement("INSERT INTO SHAREDTICKETS (ID, NAME, CONTENT, LOCKBY) VALUES (?, ?, ?, ?)")) {
				insertSt.setString(1, tableId);
				insertSt.setString(2, tableName);
				insertSt.setBytes(3, content);
				insertSt.setString(4, lockby);
				insertSt.executeUpdate();
				// sharedTicket = new SharedTicket(tableId, tableName, content, lockby);
			} catch (SQLException e) {
				logger.error("Failed to insert SHAREDTICKETS row for tableId={}", tableId, e);
			}
		}

		// now we have to decode content and create orderitem with orderlines and then
		// return them as response
		return EntityModel.of(orderitem);
	}
	// end::get-single-item[]

	@PutMapping("/orderitem/{id}")
	OrderItem replaceOrderItem(@RequestBody OrderItem newOrderItem, @PathVariable String id) {
		logger.info("PUT /orderitem request was called for id={}", id);
		// printInstalledPrinters();
		var ticketInfo = new TicketInfo();
		ticketInfo.SetInfo(id);
		if (newOrderItem.getLines() != null) {
			newOrderItem.getLines().forEach(line -> {
				if (line.getQty() <= 0) {
					logger.warn("Skipping line with non-positive quantity: productId={}, productName={}, qty={}",
							line.getProductId(), line.getProductName(), line.getQty());
					return;
				}
				var proinfoext = new com.openbravo.pos.ticket.ProductInfoExt();
				proinfoext.setID(line.getProductId());
				proinfoext.setName(line.getProductName());
				proinfoext.setPriceSell(line.getPricesell());
				// proinfoext.setAttributeSetID(line.getAttSetInstDesc());

				// get all infos from product
				try (PreparedStatement st = LoadDatabase.DBConnection
						.prepareStatement(
								"SELECT ID, REFERENCE, CODE, NAME, PRICEBUY, PRICESELL, TAXCAT, CATEGORY, ATTRIBUTESET_ID, BGCOLOR, UNIT "
										+ "FROM PRODUCTS WHERE ID = ?")) {
					st.setString(1, line.getProductId());
					try (ResultSet rs = st.executeQuery()) {
						if (rs.next()) {
							proinfoext.setID(rs.getString("ID"));
							proinfoext.setName(rs.getString("NAME"));
							proinfoext.setPriceSell(rs.getDouble("PRICESELL"));
							proinfoext.setAttributeSetID(rs.getString("ATTRIBUTESET_ID"));
							proinfoext.setCategoryID(rs.getString("CATEGORY"));
							proinfoext.setCode(rs.getString("CODE"));
							proinfoext.setReference(rs.getString("REFERENCE"));
							proinfoext.setPriceBuy(rs.getDouble("PRICEBUY"));
							proinfoext.setTaxCategoryID(rs.getString("TAXCAT"));
							proinfoext.setBgColor(rs.getString("BGCOLOR"));
							proinfoext.setUnit(rs.getString("UNIT"));
						}
					}
				} catch (SQLException e) {
					logger.error("Failed to fetch product info for id={}", line.getProductId(), e);
				}
				var ticketLineInfo = new TicketLineInfo(proinfoext, line.getQty(), line.getPricesell(), null,
						new Properties(), false, null, null, null, null);
				ticketLineInfo.setProductAttSetInstDesc(line.getAttSetInstDesc());
				ticketInfo.getLines().add(ticketLineInfo);
			});
		}
		if (ticketInfo.getLines() == null || ticketInfo.getLines().isEmpty()) {
			// remove it from sharedtickets table
			try (PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement("DELETE FROM SHAREDTICKETS where ID = ?")) {
				st.setString(1, id);
				st.executeUpdate();
			} catch (SQLException e) {
				logger.error("Failed to delete SHAREDTICKETS for id={}", id, e);
			}
		} else {
			byte[] content = encodeContent(ticketInfo);
			try (PreparedStatement st = LoadDatabase.DBConnection
					.prepareStatement("UPDATE SHAREDTICKETS SET CONTENT = ?, LOCKBY = ? where ID = ?")) {
				st.setBytes(1, content);
				st.setString(2, newOrderItem.getLockby());
				st.setString(3, id);
				st.executeUpdate();
			} catch (SQLException e) {
				logger.error("Failed to update SHAREDTICKETS for id={}", id, e);
			}
		}
		ticketPrintService.printOrderTicket(id, newOrderItem);
		return newOrderItem;
	}

	// not used any more, but kept for reference
	@DeleteMapping("/orderitem/{id}")
	void deleteOrderItem(@PathVariable String id) {
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("DELETE FROM SHAREDTICKETS where ID = ?")) {
			st.setString(1, id);
			st.executeUpdate();
		} catch (SQLException e) {
			logger.error("Failed to delete SHAREDTICKETS for id={}", id, e);
		}
	}

	void printAllPrinters() {
		try (PreparedStatement st = LoadDatabase.DBConnection.prepareStatement("SELECT ID, NAME FROM PRINTERS");
				ResultSet rs = st.executeQuery()) {
			while (rs.next()) {
				String id = rs.getString("ID");
				String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
				logger.info("Printer ID: {}, Name: {}", id, name);
			}
		} catch (SQLException e) {
			logger.error("Failed to fetch printers", e);
		}
	}

	void printInstalledPrinters() {
		PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);

		if (printers.length == 0) {
			System.out.println("No printers found.");
			return;
		}

		for (PrintService printer : printers) {
			System.out.println(printer.getName());
		}
	}

	@DeleteMapping("/TicketInfo/{id}")
	void deleteTicketInfo(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
