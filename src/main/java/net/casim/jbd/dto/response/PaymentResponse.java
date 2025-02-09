package net.casim.jbd.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "Response for a payment operation")
public class PaymentResponse {
    @Schema(description = "Number of installments paid", example = "3")
    private int installmentsPaid;

    @Schema(description = "Total amount paid", example = "1050.00")
    private BigDecimal totalAmountPaid;

    @Schema(description = "True if the loan is fully paid", example = "true")
    private boolean loanPaid;
}
