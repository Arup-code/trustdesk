package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.ToolActionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ToolActionRequestRepository extends JpaRepository<ToolActionRequest, String> {
    Optional<ToolActionRequest> findByToolNameAndIdempotencyKey(String toolName, String idempotencyKey);
}
