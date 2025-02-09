package net.casim.jbd.service;

import net.casim.jbd.config.security.CustomUserDetails;
import net.casim.jbd.dto.request.CreateLoanRequest;
import net.casim.jbd.dto.request.PaymentRequest;
import net.casim.jbd.dto.response.PaymentResponse;
import net.casim.jbd.entity.Customer;
import net.casim.jbd.entity.Loan;
import net.casim.jbd.entity.LoanInstallment;
import net.casim.jbd.utils.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class LoanService {

    private static final Logger logger = LoggerFactory.getLogger(LoanService.class);

    private final LoanHelper loanHelper;

    public LoanService(LoanHelper loanHelper) {
        this.loanHelper = loanHelper;
    }

    /**
     * Creates a loan after validating the request, checking role-based access,
     * and ensuring concurrency safety.
     */
    @Transactional
    @Retryable(retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500))
    public Loan createLoan(CreateLoanRequest request) {
        logger.info("Starting loan creation with request: {}", request);

        // --- Role-Based Access Control ---
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        CustomUserDetails currentUser = (CustomUserDetails) auth.getPrincipal();
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !currentUser.getId().equals(request.getCustomerId())) {
            throw new AccessDeniedException("Customers can only create loans for themselves");
        }
        // ----------------------------------

        // Validate the loan request.
        loanHelper.validateLoanRequest(request);

        // Fetch the customer.
        Customer customer = loanHelper.fetchCustomerById(request.getCustomerId());

        // Ensure that the customer's credit limit is not exceeded.
        BigDecimal newUsedCredit = customer.getUsedCreditLimit().add(request.getAmount());
        if (newUsedCredit.compareTo(customer.getCreditLimit()) > 0) {
            logger.error("Insufficient credit limit for customer id {}. Used: {}, Limit: {}",
                    customer.getId(), customer.getUsedCreditLimit(), customer.getCreditLimit());
            throw new IllegalArgumentException("Customer does not have enough credit limit");
        }

        // Create and save the loan.
        Loan loan = new Loan(customer.getId(), request.getAmount(), request.getNumberOfInstallments());
        loan = loanHelper.saveLoan(loan);
        logger.info("Loan created with id: {}", loan.getId());

        // Update the customer's used credit limit.
        customer.addUsedCredit(request.getAmount());
        loanHelper.saveCustomer(customer);
        logger.info("Updated customer's used credit limit to {} for customer id: {}",
                customer.getUsedCreditLimit(), customer.getId());

        // Calculate total repayment and determine installment amount.
        BigDecimal totalRepayment = request.getAmount().multiply(BigDecimal.ONE.add(request.getInterestRate()));
        BigDecimal installmentAmount = totalRepayment.divide(
                new BigDecimal(request.getNumberOfInstallments()), 2, RoundingMode.HALF_UP);

        // Create and save installments.
        List<LoanInstallment> installments = loanHelper.createLoanInstallments(loan, installmentAmount, request.getNumberOfInstallments());
        loanHelper.saveInstallments(installments);
        logger.info("All installments created for loan id {}", loan.getId());

        return loan;
    }

    /**
     * Deletes a loan. If the loan is not fully paid, subtracts the loan amount
     * from the customer's used credit limit before deletion.
     * This method is intended for admin usage.
     */
    @Transactional
    @Retryable(retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500))
    public void deleteLoan(Long loanId) {
        logger.info("Attempting to delete loan with id: {}", loanId);

        Loan loan = loanHelper.fetchLoanById(loanId);
        Customer customer = loanHelper.fetchCustomerById(loan.getCustomerId());

        if (!loan.isPaid()) {
            customer.subtractUsedCredit(loan.getLoanAmount());
            loanHelper.saveCustomer(customer);
            logger.info("Updated customer's used credit limit after deleting loan id: {}", loan.getId());
        }

        loanHelper.deleteLoan(loan);
        logger.info("Loan with id {} has been deleted.", loan.getId());
    }

    /**
     * Retrieves loans for a given customer and computes their remaining fee.
     */
    public List<Loan> getLoansForCustomer(Long customerId) {
        logger.info("Retrieving loans for customer id: {}", customerId);
        List<Loan> loans = loanHelper.fetchLoanByCustomerId(customerId);
        LocalDate today = LocalDate.now();

        loans.forEach(loan -> {
            BigDecimal totalRemaining = loan.getInstallments().stream()
                    .filter(installment -> !installment.isPaid())
                    .map(installment -> loanHelper.calculateEffectiveInstallmentAmount(
                            installment.getAmount(), today, installment.getDueDate()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            loan.setRemainingFee(totalRemaining.setScale(2, RoundingMode.HALF_UP));
        });
        return loans;
    }

    /**
     * Retrieves the installments for a specific loan in ascending order by due date.
     */
    public List<LoanInstallment> getInstallmentsForLoan(Long loanId) {
        logger.info("Retrieving installments for loan id: {}", loanId);
        return loanHelper.findInstallmentsByLoanIdOrderByDueDateAsc(loanId);
    }

    /**
     * Processes a payment for a loan while ensuring concurrency safety.
     * This method applies the incoming payment to the loan installments in order.
     * The business rule enforced is that installments must be paid wholly or not at all.
     * For example, if an installment's effective amount is 10 and the payment amount is 20,
     * then two installments are paid. If the payment amount is 15, only one installment is paid;
     * if the payment amount is 5, no installment is paid.
     */
    @Transactional
    @Retryable(retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500))
    public PaymentResponse payLoan(Long loanId, PaymentRequest paymentRequest) {
        logger.info("Processing payment for loan id {} with payment amount {}",
                loanId, paymentRequest.getAmount());

        // Retrieve the loan and its installments.
        Loan loan = loanHelper.fetchLoanById(loanId);
        List<LoanInstallment> installments = loanHelper.findInstallmentsByLoanIdOrderByDueDateAsc(loanId);
        LocalDate today = LocalDate.now();
        LocalDate paymentWindowThreshold = today.plusMonths(3).with(TemporalAdjusters.firstDayOfMonth());
        logger.info("Payment window threshold set to {}", paymentWindowThreshold);

        BigDecimal remainingPayment = paymentRequest.getAmount();
        int installmentsPaid = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        // Iterate over installments in order (earliest first).
        for (LoanInstallment installment : installments) {
            // Only consider installments that are not already paid and are eligible for payment.
            if (installment.isPaid() || !installment.getDueDate().isBefore(paymentWindowThreshold)) {
                continue;
            }

            // Calculate the effective installment amount (including any discount or penalty).
            BigDecimal effectiveAmount = loanHelper.calculateEffectiveInstallmentAmount(
                    installment.getAmount(), today, installment.getDueDate());
            logger.debug("Installment id {}: effective amount is {} (base amount: {})",
                    installment.getId(), effectiveAmount, installment.getAmount());

            // Check if the remaining payment can fully cover this installment.
            if (remainingPayment.compareTo(effectiveAmount) >= 0) {
                // Mark the installment as fully paid.
                installment.markAsPaid(effectiveAmount, today);
                loanHelper.saveInstallment(installment);

                // Deduct the paid amount from the remaining payment.
                remainingPayment = remainingPayment.subtract(effectiveAmount);
                totalPaid = totalPaid.add(effectiveAmount);
                installmentsPaid++;
                logger.info("Paid installment id {} with amount {}", installment.getId(), effectiveAmount);
            } else {
                // If remaining payment is not enough to cover this installment completely,
                // then no partial payment is made and processing stops.
                logger.debug("Remaining payment {} is insufficient for installment id {} (requires {})",
                        remainingPayment, installment.getId(), effectiveAmount);
                break;
            }
        }

        // If no installment was fully paid, throw an exception.
        if (installmentsPaid == 0) {
            String errorMsg = "Payment failed: Provided amount " + paymentRequest.getAmount()
                    + " is insufficient to cover any eligible installment.";
            logger.error(errorMsg);
            throw new PaymentException(errorMsg);
        }

        // Check if all installments are now paid.
        boolean loanFullyPaid = installments.stream().allMatch(LoanInstallment::isPaid);
        if (loanFullyPaid) {
            // If so, mark the loan as paid and adjust the customer's used credit.
            loan.markAsPaid();
            loanHelper.saveLoan(loan);
            logger.info("Loan id {} has been fully paid", loan.getId());

            Customer customer = loanHelper.fetchCustomerById(loan.getCustomerId());
            customer.subtractUsedCredit(loan.getLoanAmount());
            loanHelper.saveCustomer(customer);
            logger.info("Customer id {} used credit limit updated after full loan repayment", customer.getId());
        }

        PaymentResponse response = new PaymentResponse(installmentsPaid, totalPaid, loanFullyPaid);
        logger.info("Payment processing complete: {}", response);
        return response;
    }

}
