package net.casim.jbd.service;

import net.casim.jbd.dto.request.CreateLoanRequest;
import net.casim.jbd.entity.Customer;
import net.casim.jbd.entity.Loan;
import net.casim.jbd.entity.LoanInstallment;
import net.casim.jbd.entity.enums.InstallmentCount;
import net.casim.jbd.repository.CustomerRepository;
import net.casim.jbd.repository.LoanInstallmentRepository;
import net.casim.jbd.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class LoanHelper {

    private static final Logger logger = LoggerFactory.getLogger(LoanHelper.class);

    private final BigDecimal minInterest;
    private final BigDecimal maxInterest;

    private final CustomerRepository customerRepository;
    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;

    public LoanHelper(CustomerRepository customerRepository,
                      LoanRepository loanRepository,
                      LoanInstallmentRepository installmentRepository, @Value("${loan.minInterest}") BigDecimal minInterest, @Value("${loan.maxInterest}") BigDecimal maxInterest) {
        this.customerRepository = customerRepository;
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
        this.minInterest = minInterest;
        this.maxInterest = maxInterest;
    }

    /**
     * Validates the loan request parameters.
     * Throws IllegalArgumentException if any validation fails.
     */
    public void validateLoanRequest(CreateLoanRequest request) {
        if (!InstallmentCount.isAllowed(request.getNumberOfInstallments())) {
            logger.error("Invalid installment count: {}", request.getNumberOfInstallments());
            throw new IllegalArgumentException("Allowed installment counts: 6, 9, 12, 24");
        }

        if (request.getInterestRate().compareTo(minInterest) < 0 ||
                request.getInterestRate().compareTo(maxInterest) > 0) {
            logger.error("Invalid interest rate: {}", request.getInterestRate());
            throw new IllegalArgumentException("Interest rate must be between " + minInterest + " and " + maxInterest);
        }
    }

    /**
     * Creates loan installments and returns the list.
     * The installments are built starting on the first day of the next month.
     */
    public List<LoanInstallment> createLoanInstallments(Loan loan, BigDecimal installmentAmount, int numberOfInstallments) {
        LocalDate firstDueDate = LocalDate.now().plusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        logger.info("Creating {} installments starting from {}", numberOfInstallments, firstDueDate);

        return IntStream.range(0, numberOfInstallments)
                .mapToObj(i -> {
                    LocalDate dueDate = firstDueDate.plusMonths(i);
                    LoanInstallment installment = new LoanInstallment(loan, installmentAmount, dueDate);
                    logger.debug("Prepared installment for loan id {}: due date {} amount {}",
                            loan.getId(), dueDate, installmentAmount);
                    return installment;
                })
                .collect(Collectors.toList());
    }

    /**
     * Computes the effective installment amount.
     * Applies a discount for early payment (if payment is made before due date)
     * or a penalty for late payment.
     */
    public BigDecimal calculateEffectiveInstallmentAmount(BigDecimal baseAmount, LocalDate today, LocalDate dueDate) {
        long daysDiff = ChronoUnit.DAYS.between(today, dueDate);
        BigDecimal adjustmentFactor = baseAmount.multiply(new BigDecimal("0.001"))
                .multiply(new BigDecimal(Math.abs(daysDiff)));
        BigDecimal effectiveAmount = baseAmount;
        if (daysDiff > 0) {
            // Early payment discount.
            effectiveAmount = effectiveAmount.subtract(adjustmentFactor);
        } else if (daysDiff < 0) {
            // Late payment penalty.
            effectiveAmount = effectiveAmount.add(adjustmentFactor);
        }
        return effectiveAmount.setScale(2, RoundingMode.HALF_UP);
    }


    public Loan fetchLoanById(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> {
                    logger.error("Loan not found with id: {}", loanId);
                    return new IllegalArgumentException("Loan not found with id: " + loanId);
                });
    }

    public Customer fetchCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    logger.error("Customer not found with id: {}", customerId);
                    return new IllegalArgumentException("Customer not found with id: " + customerId);
                });
    }

    public List<Loan> fetchLoanByCustomerId(Long customerId) {
        fetchCustomerById(customerId);
        return loanRepository.findByCustomerId(customerId);
    }
    
    public List<Loan> findByCustomerIdAndIsPaid(Long customerId, boolean isPaid) {
        fetchCustomerById(customerId);
        return loanRepository.findByCustomerIdAndIsPaid(customerId, isPaid);
    }
    
    public List<Loan> findByCustomerIdAndNumberOfInstallment(Long customerId, int numberOfInstallment) {
        fetchCustomerById(customerId);
        return loanRepository.findByCustomerIdAndNumberOfInstallment(customerId, numberOfInstallment);
    }
    
    public List<Loan> findByCustomerIdAndIsPaidAndNumberOfInstallment(
            Long customerId, boolean isPaid, int numberOfInstallment) {
        fetchCustomerById(customerId);
        return loanRepository.findByCustomerIdAndIsPaidAndNumberOfInstallment(
                customerId, isPaid, numberOfInstallment);
    }
    
    public List<Loan> findByCustomerIdAndDateRange(
            Long customerId, LocalDate startDate, LocalDate endDate) {
        fetchCustomerById(customerId);
        return loanRepository.findByCustomerIdAndDateRange(customerId, startDate, endDate);
    }

    public Loan saveLoan(Loan loan) {
        return loanRepository.save(loan);
    }

    public void saveCustomer(Customer customer) {
        customerRepository.save(customer);
    }

    public void saveInstallments(List<LoanInstallment> installments) {
        installmentRepository.saveAll(installments);
    }

    public List<LoanInstallment> findInstallmentsByLoanIdOrderByDueDateAsc(Long loanId) {
        List<LoanInstallment> installments = installmentRepository.findByLoanIdOrderByDueDateAsc(loanId);
        if (installments.isEmpty()) {
            throw new IllegalArgumentException("No installments found for loan id: " + loanId);
        }
        return installments;
    }

    public void saveInstallment(LoanInstallment installment) {
        installmentRepository.save(installment);
    }

    public void deleteLoan(Loan loan) {
        loanRepository.delete(loan);
    }
}
