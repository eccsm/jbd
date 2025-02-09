package net.casim.jbd.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.casim.jbd.dto.request.LoginRequest;
import net.casim.jbd.dto.request.SignupRequest;
import net.casim.jbd.dto.response.TokenResponse;
import net.casim.jbd.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and registration")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "User Signup",
            description = "Registers a new user and creates an associated customer record. The username is generated from the first letter of the name combined with the surname (lowercase)."
    )
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest) {
        String generatedUsername = authService.signup(signupRequest);
        return ResponseEntity.ok("User registered successfully with username: " + generatedUsername);
    }

    @Operation(
            summary = "User Login",
            description = "Authenticates a user and returns a JWT token if successful."
    )
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        TokenResponse tokenResponse = authService.login(loginRequest);
        return ResponseEntity.ok(tokenResponse);
    }
}
