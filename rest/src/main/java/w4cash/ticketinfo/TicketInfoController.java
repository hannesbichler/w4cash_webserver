package w4cash.ticketinfo;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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

import com.openbravo.pos.ticket.TicketInfo;

import w4cash.LoadDatabase;

// tag::hateoas-imports[]
// end::hateoas-imports[]

@RestController
class TicketInfoController {

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

	private OrderItem decodeContent(byte[] content) {
		// implement your decoding logic here to convert byte array back to OrderItem
		ObjectInputStream in;
		var orderItem = new OrderItem();
		try {
			in = new ObjectInputStream(new ByteArrayInputStream(content));
			var ticketInfo = (TicketInfo) in.readObject();
			ticketInfo.getLines().forEach(line -> {
				orderItem.getLines().add(new OrderLine(
						"", "", line.getProductID(), HtmlUtils.htmlEscape(line.getProductName()), line.getPrice(),
						line.getMultiply()));
				System.out.println(line.getProductName());
			});

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return orderItem;
	}

	// tag::get-single-item[]
	@GetMapping("/orderitem/{id}")
	EntityModel<OrderItem> one(@PathVariable String id) {

		SharedTicket sharedTicket = null;
		try (PreparedStatement st = LoadDatabase.DBConnection
				.prepareStatement("SELECT * FROM SHAREDTICKETS where ID = ?")) {
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
		} else {
			orderitem = new OrderItem();
		}

		// now we have to decode content and create orderitem with orderlines and then
		// return them as response
		return EntityModel.of(orderitem// , //
		// linkTo(methodOn(TicketInfoController.class).one(id)).withSelfRel(),
		// linkTo(methodOn(TicketInfoController.class).all()).withRel("ticketInfos")
		);
	}
	// end::get-single-item[]

	@PutMapping("/TicketInfo/{id}")
	SharedTicket replaceSharedTicket(@RequestBody SharedTicket newSharedTicket, @PathVariable Long id) {

		return repository.findById(id) //
				.map(sharedTicket -> {
					// sharedTicket.setName(newSharedTicket.getName());
					return repository.save(sharedTicket);
				}) //
				.orElseGet(() -> {
					return repository.save(newSharedTicket);
				});
	}

	@DeleteMapping("/TicketInfo/{id}")
	void deleteTicketInfo(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
