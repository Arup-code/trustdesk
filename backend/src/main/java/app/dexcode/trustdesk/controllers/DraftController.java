package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.DraftResponse;
import app.dexcode.trustdesk.services.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class DraftController {

    private final TicketService ticketService;

    public DraftController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets/{id}/draft-reply")
    public ResponseEntity<DraftResponse> draftReply(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ticketService.generateDraft(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
