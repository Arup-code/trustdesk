package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.services.TicketService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TriageController {

    private final TicketService ticketService;

    public TriageController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets/{id}/triage")
    public TriageResponse triage(@PathVariable String id) {
        return ticketService.runTriage(id);
    }
}
