package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.services.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class TriageController {

    private final TicketService ticketService;

    public TriageController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets/{id}/triage")
    public ResponseEntity<TriageResponse> triage(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ticketService.runTriage(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
