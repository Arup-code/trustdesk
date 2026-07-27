package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.services.ToolActionService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/tool-actions")
public class ToolActionController {

    private final ToolActionService toolActionService;

    public ToolActionController(ToolActionService toolActionService) {
        this.toolActionService = toolActionService;
    }

    public record RequestActionBody(
        @JsonProperty("ticket_id") String ticketId,
        @JsonProperty("tool_name") String toolName,
        Map<String, Object> payload
    ) {}

    public record ApproveBody(
        @JsonProperty("reviewer_id") String reviewerId,
        String decision,
        String reason
    ) {}

    @PostMapping
    public ResponseEntity<?> requestAction(@RequestBody RequestActionBody body) {
        try {
            ToolActionRequest action =
                toolActionService.requestAction(body.ticketId(), body.toolName(), body.payload());
            return ResponseEntity.status(HttpStatus.CREATED).body(action);
        } catch (ToolActionService.ToolActionValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (ToolActionService.ToolActionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestBody ApproveBody body) {
        try {
            Approval approval =
                toolActionService.approve(id, body.reviewerId(), body.decision(), body.reason());
            return ResponseEntity.ok(approval);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (ToolActionService.InvalidToolActionStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> execute(@PathVariable String id) {
        try {
            ToolActionRequest action = toolActionService.execute(id);
            return ResponseEntity.ok(action);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (ToolActionService.InvalidToolActionStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
