package net.casim.jbd.initializer;

import net.casim.jbd.dto.request.SignupRequest;
import net.casim.jbd.repository.UserRepository;
import net.casim.jbd.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final AuthService authService;
    private final UserRepository userRepository;

    public DataInitializer(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            logger.info("Users already exist in the database. Skipping data initialization.");
            return;
        }

        logger.info("Initializing starter data...");

        List<SignupRequest> signupRequests = List.of(
                createSignupRequest("Alice", "Admin", "admin1", List.of("ADMIN")),
                createSignupRequest("Bob", "Admin", "admin2", List.of("ADMIN")),
                createSignupRequest("Charlie", "Customer", "cust1", List.of("CUSTOMER")),
                createSignupRequest("Daisy", "Customer", "cust2", List.of("CUSTOMER")),
                createSignupRequest("Eve", "Customer", "cust3", List.of("CUSTOMER"))
        );

        for (SignupRequest request : signupRequests) {
            try {
                String username = authService.signup(request);
                logger.info("Created user: {} with roles {}", username, request.getRoles());
            } catch (Exception ex) {
                logger.error("Error creating user for {} {}: {}", request.getName(), request.getSurname(), ex.getMessage());
            }
        }

        logger.info("Data initialization complete.");
    }

    private SignupRequest createSignupRequest(String name, String surname, String password, List<String> roles) {
        SignupRequest request = new SignupRequest();
        request.setName(name);
        request.setSurname(surname);
        request.setPassword(password);
        request.setRoles(roles);
        return request;
    }
}
