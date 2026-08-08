package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.AgentRunTrace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunTraceRepository extends JpaRepository<AgentRunTrace, String> {}
