package net.casim.jbd.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request payload for user signup")
public class SignupRequest {

    @NotBlank(message = "Name cannot be blank")
    @Schema(description = "User's first name", example = "John")
    private String name;

    @NotBlank(message = "Surname cannot be blank")
    @Schema(description = "User's surname", example = "Doe")
    private String surname;

    @NotBlank(message = "Password cannot be blank")
    @Schema(description = "User's password", example = "secretPassword123")
    private String password;

    @NotEmpty(message = "At least one role must be specified")
    @Schema(description = "List of roles assigned to the user. Example roles: ADMIN, CUSTOMER", example = "[\"CUSTOMER\"]")
    private List<String> roles;
}
