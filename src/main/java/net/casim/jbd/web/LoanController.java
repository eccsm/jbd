package net.casim.jbd.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.casim.jbd.dto.request.CreateLoanRequest;
import net.casim.jbd.dto.request.PaymentRequest;
import net.casim.jbd.dto.response.PaymentResponse;
import net.casim.jbd.entity.Loan;
import net.casim.jbd.entity.LoanInstallment;
import net.casim.jbd.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loans")
@Tag(name = "Loans", description = "Endpoints for loan management")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(
            summary = "Create Loan",
            description = "Creates a new loan for a customer. Customers can only create loans for themselves; admins can create loans for any customer."
    )
    @PostMapping
    public ResponseEntity<Loan> createLoan(@Valid @RequestBody CreateLoanRequest request) {
        Loan loan = loanService.createLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(loan);
    }

    @Operation(
            summary = "Get Loans",
            description = "Retrieves all loans for a specified customer, including computed remaining fees."
    )
    @GetMapping
    public ResponseEntity<List<Loan>> getLoans(@RequestParam Long customerId) {
        List<Loan> loans = loanService.getLoansForCustomer(customerId);
        return ResponseEntity.ok(loans);
    }

    @Operation(
            summary = "Get Loan Installments",
            description = "Retrieves all installments for a given loan, ordered by due date."
    )
    @GetMapping("/{loanId}/installments")
    public ResponseEntity<List<LoanInstallment>> getInstallments(@PathVariable Long loanId) {
        List<LoanInstallment> installments = loanService.getInstallmentsForLoan(loanId);
        return ResponseEntity.ok(installments);
    }

    @Operation(
            summary = "Pay Loan",
            description = "Processes a payment for a loan by applying the payment amount to eligible installments."
    )
    @PostMapping("/{loanId}/pay")
    public ResponseEntity<PaymentResponse> payLoan(@PathVariable Long loanId, @Valid @RequestBody PaymentRequest paymentRequest) {
        PaymentResponse response = loanService.payLoan(loanId, paymentRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Delete Loan",
            description = "Deletes a loan. If the loan is not fully paid, the customer's used credit limit is adjusted accordingly. (Admin only)"
    )
    @DeleteMapping("/{loanId}")
    public ResponseEntity<Void> deleteLoan(@PathVariable Long loanId) {
        loanService.deleteLoan(loanId);
        return ResponseEntity.ok().build();
    }
}
