package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, String> {}
