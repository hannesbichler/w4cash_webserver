package w4cash.ticketinfo;

class TicketInfoNotFoundException extends RuntimeException {

	TicketInfoNotFoundException(Long id) {
		super("Could not find order " + id);
	}
}
