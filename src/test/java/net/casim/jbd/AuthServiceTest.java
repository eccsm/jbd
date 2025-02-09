package net.casim.jbd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import net.casim.jbd.dto.request.LoginRequest;
import net.casim.jbd.dto.request.SignupRequest;
import net.casim.jbd.dto.response.TokenResponse;
import net.casim.jbd.entity.Customer;
import net.casim.jbd.entity.User;
import net.casim.jbd.entity.enums.Role;
import net.casim.jbd.repository.CustomerRepository;
import net.casim.jbd.repository.UserRepository;
import net.casim.jbd.service.AuthService;
import net.casim.jbd.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    public void setUp() {

    }

    @Test
    public void testSignup_HappyPath_WithRoles() {
        // Prepare a signup request.
        SignupRequest request = new SignupRequest();
        request.setName("John");
        request.setSurname("Doe");
        request.setPassword("secret");
        request.setRoles(List.of("ADMIN", "CUSTOMER"));

        // The generated username should be: first letter of name + surname, in lowercase.
        String generatedUsername = "jdoe";

        // Stub: When finding by username, no user exists.
        when(userRepository.findByUsername(generatedUsername)).thenReturn(Optional.empty());
        // Stub: Simulate password encoding.
        when(passwordEncoder.encode("secret")).thenReturn("encodedSecret");

        // When saving the user, simply return the same user with an ID.
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername(generatedUsername);
        savedUser.setPassword("encodedSecret");
        savedUser.setRoles(List.of(Role.ADMIN, Role.CUSTOMER));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When saving the customer, simply do nothing (or return the same customer).
        Customer savedCustomer = new Customer(request.getName(), request.getSurname(), new BigDecimal("10000"), savedUser);
        savedCustomer.setId(1L);
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        // Call the signup method.
        String resultUsername = authService.signup(request);

        // Verify that the generated username is returned.
        assertEquals(generatedUsername, resultUsername);
        verify(userRepository, times(1)).findByUsername(generatedUsername);
        verify(userRepository, times(1)).save(any(User.class));
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    public void testSignup_HappyPath_DefaultRole() {
        // Prepare a signup request with no roles.
        SignupRequest request = new SignupRequest();
        request.setName("Alice");
        request.setSurname("Smith");
        request.setPassword("password123");
        // Leave roles null to trigger the default
        request.setRoles(Collections.emptyList());

        // Expected generated username: "asmith"
        String generatedUsername = "asmith";

        when(userRepository.findByUsername(generatedUsername)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");

        // Stub the user and customer save operations.
        User savedUser = new User();
        savedUser.setId(2L);
        savedUser.setUsername(generatedUsername);
        savedUser.setPassword("encodedPassword123");
        // Since no roles were provided, default should be CUSTOMER.
        savedUser.setRoles(Collections.singletonList(Role.CUSTOMER));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Customer savedCustomer = new Customer(request.getName(), request.getSurname(), new BigDecimal("10000"), savedUser);
        savedCustomer.setId(2L);
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        String resultUsername = authService.signup(request);
        assertEquals(generatedUsername, resultUsername);
        verify(userRepository).findByUsername(generatedUsername);
        verify(userRepository).save(any(User.class));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    public void testSignup_UsernameAlreadyExists() {
        // Prepare a signup request.
        SignupRequest request = new SignupRequest();
        request.setName("Bob");
        request.setSurname("Marley");
        request.setPassword("reggae");
        request.setRoles(List.of("CUSTOMER"));

        // Expected generated username: "bmarley"
        String generatedUsername = "bmarley";

        // Simulate that a user already exists with that username.
        User existingUser = new User();
        existingUser.setId(99L);
        existingUser.setUsername(generatedUsername);
        when(userRepository.findByUsername(generatedUsername)).thenReturn(Optional.of(existingUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.signup(request);
        });
        // Expect HTTP CONFLICT status.
        assertEquals(HttpStatus.CONFLICT.toString(), ex.getStatusCode().toString());
        assertTrue(ex.getReason().contains("Username already exists"));

        verify(userRepository, times(1)).findByUsername(generatedUsername);
        verify(userRepository, never()).save(any(User.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    public void testLogin_HappyPath() {
        // Prepare a login request.
        LoginRequest request = new LoginRequest("jdoe", "secret");

        // Prepare a dummy user.
        User user = new User();
        user.setId(1L);
        user.setUsername("jdoe");
        // Simulate an encoded password.
        user.setPassword("encodedSecret");
        user.setRoles(List.of(Role.CUSTOMER));

        // Stub the repository: return the user.
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        // Stub the password encoder: when matching, return true.
        when(passwordEncoder.matches("secret", "encodedSecret")).thenReturn(true);
        // Stub jwtUtil to return a dummy token.
        when(jwtUtil.generateToken(user)).thenReturn("dummy-jwt-token");

        TokenResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("dummy-jwt-token", response.getToken());

        verify(userRepository, times(1)).findByUsername("jdoe");
        verify(passwordEncoder, times(1)).matches("secret", "encodedSecret");
        verify(jwtUtil, times(1)).generateToken(user);
    }

    @Test
    public void testLogin_UserNotFound() {
        // Prepare a login request.
        LoginRequest request = new LoginRequest("nonexistent", "whatever");

        // Stub repository to return empty.
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED.toString(), ex.getStatusCode().toString());
        assertTrue(ex.getReason().contains("Invalid credentials"));

        verify(userRepository, times(1)).findByUsername("nonexistent");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    public void testLogin_InvalidPassword() {
        // Prepare a login request.
        LoginRequest request = new LoginRequest("jdoe", "wrongPassword");

        // Prepare a dummy user.
        User user = new User();
        user.setId(1L);
        user.setUsername("jdoe");
        user.setPassword("encodedSecret");
        user.setRoles(List.of(Role.CUSTOMER));

        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        // Simulate that the password does not match.
        when(passwordEncoder.matches("wrongPassword", "encodedSecret")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED.toString(), ex.getStatusCode().toString());
        assertTrue(ex.getReason().contains("Invalid credentials"));

        verify(userRepository, times(1)).findByUsername("jdoe");
        verify(passwordEncoder, times(1)).matches("wrongPassword", "encodedSecret");
        verify(jwtUtil, never()).generateToken(any());
    }
}
