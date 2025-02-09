package net.casim.jbd.service;

import net.casim.jbd.dto.request.LoginRequest;
import net.casim.jbd.dto.request.SignupRequest;
import net.casim.jbd.dto.response.TokenResponse;
import net.casim.jbd.entity.Customer;
import net.casim.jbd.entity.User;
import net.casim.jbd.repository.CustomerRepository;
import net.casim.jbd.repository.UserRepository;
import net.casim.jbd.utils.JwtUtil;
import net.casim.jbd.entity.enums.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       CustomerRepository customerRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Signs up a new user and creates an associated customer record.
     *
     * @param signupRequest the signup request (already validated by the controller)
     * @return the generated username for the new user
     */
    public String signup(SignupRequest signupRequest) {
        // Generate a username from the first letter of the name plus the surname.
        String generatedUsername = (signupRequest.getName().charAt(0) + signupRequest.getSurname()).toLowerCase();

        // Check if the username already exists.
        Optional<User> existingUser = userRepository.findByUsername(generatedUsername);
        if (existingUser.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists. Please change your name or contact support.");
        }

        // Create a new user entity.
        User user = new User();
        user.setUsername(generatedUsername);
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));

        // Process the roles. If none provided, default to CUSTOMER.
        List<Role> roles;
        if (signupRequest.getRoles() == null || signupRequest.getRoles().isEmpty()) {
            roles = Collections.singletonList(Role.CUSTOMER);
        } else {
            roles = signupRequest.getRoles().stream()
                    .map(roleStr -> {
                        try {
                            return Role.valueOf(roleStr.toUpperCase());
                        } catch (IllegalArgumentException ex) {
                            return Role.CUSTOMER;
                        }
                    })
                    .collect(Collectors.toList());
        }
        user.setRoles(roles);

        userRepository.save(user);

        // Create an associated customer record with a default credit limit.
        BigDecimal creditLimit = new BigDecimal("10000");
        Customer customer = new Customer(
                signupRequest.getName(),
                signupRequest.getSurname(),
                creditLimit,
                user
        );
        customerRepository.save(customer);

        return generatedUsername;
    }

    /**
     * Authenticates a user and returns a JWT token if successful.
     *
     * @param loginRequest the login request (already validated by the controller)
     * @return a TokenResponse containing the JWT token
     */
    public TokenResponse login(LoginRequest loginRequest) {
        Optional<User> userOpt = userRepository.findByUsername(loginRequest.getUsername());
        if (userOpt.isEmpty() || !passwordEncoder.matches(loginRequest.getPassword(), userOpt.get().getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user);
        return new TokenResponse(token);
    }
}
