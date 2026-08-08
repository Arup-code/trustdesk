package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {}
