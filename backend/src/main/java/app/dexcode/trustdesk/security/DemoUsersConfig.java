package app.dexcode.trustdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class DemoUsersConfig {

    public record DemoUser(String username, String passwordHash, String role) {}

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Map<String, DemoUser> demoUsers(PasswordEncoder passwordEncoder) {
        return Map.of(
            "agent1", new DemoUser("agent1", passwordEncoder.encode("agent123"), "support_agent"),
            "manager1", new DemoUser("manager1", passwordEncoder.encode("manager123"), "support_manager")
        );
    }
}
