package net.casim.jbd.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request payload for making a payment towards a loan")
public class PaymentRequest {

    @NotNull(message = "Payment amount cannot be null")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    @Schema(description = "The amount to be paid", example = "1050.00")
    private BigDecimal amount;
}
