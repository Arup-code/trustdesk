package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.services.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<Ticket> listTickets() {
        return ticketService.listTickets();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponse> getTicket(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ticketService.getTicketDetail(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
