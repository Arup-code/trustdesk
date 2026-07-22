package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.security.DemoUsersConfig;
import app.dexcode.trustdesk.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final Map<String, DemoUsersConfig.DemoUser> demoUsers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
        Map<String, DemoUsersConfig.DemoUser> demoUsers,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.demoUsers = demoUsers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, String role) {}

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        DemoUsersConfig.DemoUser user = demoUsers.get(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            return ResponseEntity.status(401).build();
        }
        String token = jwtService.issueToken(user.username(), user.role());
        return ResponseEntity.ok(new LoginResponse(token, user.username(), user.role()));
    }
}
