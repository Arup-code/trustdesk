package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {}
