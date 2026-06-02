package w4cash.ticketinfo;

import org.springframework.data.jpa.repository.JpaRepository;

interface SharedTicketRepository extends JpaRepository<SharedTicket, Long> {
}
