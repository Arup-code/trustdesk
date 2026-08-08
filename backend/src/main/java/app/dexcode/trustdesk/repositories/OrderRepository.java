package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {}
