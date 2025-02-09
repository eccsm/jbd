package net.casim.jbd.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request payload to create a new loan")
public class CreateLoanRequest {

    @NotNull(message = "Customer ID is required")
    @Schema(description = "Identifier of the customer for whom the loan is being created", example = "1")
    private Long customerId;

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "0.01", message = "Loan amount must be greater than zero")
    @Schema(description = "The principal amount of the loan", example = "1000.00")
    private BigDecimal amount;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.1", inclusive = true, message = "Interest rate must be at least 0.1")
    @DecimalMax(value = "0.5", inclusive = true, message = "Interest rate must be at most 0.5")
    @Schema(description = "Interest rate applied to the loan", example = "0.03")
    private BigDecimal interestRate;

    @NotNull(message = "Number of installments is required")
    @Schema(description = "Total number of installments (allowed: 6, 9, 12, or 24)", example = "12")
    private Integer numberOfInstallments;
}
