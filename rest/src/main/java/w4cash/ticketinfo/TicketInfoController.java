package w4cash.ticketinfo;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

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

import w4cash.LoadDatabase;
import w4cash.attribute.Attribute;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class TicketInfoController {
	private static final Logger logger = LoggerFactory.getLogger(TicketInfoController.class);

	private final SharedTicketRepository repository;

	TicketInfoController(SharedTicketRepository repository) {
		this.repository = repository;
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
			orderItem.getLines().add(new OrderLine(
					"", "", line.getProductID(), HtmlUtils.htmlEscape(line.getProductName()), line.getPrice(),
					line.getMultiply(), line.getProductAttSetId(),
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
	@GetMapping("/orderitem/{id}")
	EntityModel<OrderItem> one(@PathVariable String id) {

		SharedTicket sharedTicket = null;
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT ID, NAME, CONTENT, LOCKBY FROM SHAREDTICKETS where ID = ?")) {
			st.setString(1, id);
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					String id_ = rs.getString("ID");
					String name = HtmlUtils.htmlEscape(rs.getString("NAME"));
					byte[] content = rs.getBytes("CONTENT");
					String lockby = rs.getString("LOCKBY");
					sharedTicket = new SharedTicket(id_, name, content, lockby);
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
			// orderitem.setTickettype(sharedTicket.getTickettype());
		} else {
			orderitem = new OrderItem();
			// add new orderitem to sharedTicket and save it to database
			sharedTicket = new SharedTicket(id, "New Ticket", null, null);
			repository.save(sharedTicket);
		}

		// now we have to decode content and create orderitem with orderlines and then
		// return them as response
		return EntityModel.of(orderitem);
	}
	// end::get-single-item[]

	@PutMapping("/orderitem/{id}")
	OrderItem replaceOrderItem(@RequestBody OrderItem newOrderItem, @PathVariable String id) {
		return newOrderItem;
		/*
		 * return repository.findById(id) //
		 * .map(sharedTicket -> {
		 * // sharedTicket.setName(newSharedTicket.getName());
		 * return repository.save(sharedTicket);
		 * }) //
		 * .orElseGet(() -> {
		 * return repository.save(new SharedTicket(id, "", new byte[0], ""));
		 * });
		 */
	}

	@DeleteMapping("/TicketInfo/{id}")
	void deleteTicketInfo(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
