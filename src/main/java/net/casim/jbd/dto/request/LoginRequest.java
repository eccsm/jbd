package net.casim.jbd.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for user login")
public class LoginRequest {

    @NotBlank(message = "Username cannot be blank")
    @Schema(description = "User's username", example = "jdoe")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Schema(description = "User's password", example = "secret")
    private String password;
}
