package net.casim.jbd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import net.casim.jbd.config.security.CustomUserDetails;
import net.casim.jbd.dto.request.CreateLoanRequest;
import net.casim.jbd.dto.request.PaymentRequest;
import net.casim.jbd.dto.response.PaymentResponse;
import net.casim.jbd.entity.Customer;
import net.casim.jbd.entity.Loan;
import net.casim.jbd.entity.LoanInstallment;
import net.casim.jbd.entity.User;
import net.casim.jbd.entity.enums.Role;
import net.casim.jbd.service.LoanHelper;
import net.casim.jbd.service.LoanService;
import net.casim.jbd.utils.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
public class LoanServiceTest {

    // Set up interest boundaries.
    private final BigDecimal minInterest = new BigDecimal("0.01");
    private final BigDecimal maxInterest = new BigDecimal("0.05");

    // Inject LoanHelper (which encapsulates repository operations) into LoanService.
    @Mock
    private LoanHelper loanHelper;

    @InjectMocks
    private LoanService loanService;

    @BeforeEach
    public void setUp() {
        // Set up a dummy security context for role-based access.
        User dummyUser = new User();
        dummyUser.setId(1L);
        dummyUser.setUsername("testuser");
        dummyUser.setPassword("encoded");
        dummyUser.setRoles(Collections.singletonList(Role.CUSTOMER));
        CustomUserDetails userDetails = new CustomUserDetails(dummyUser);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Reinitialize loanService with the injected helper (if needed)
        loanService = new LoanService(loanHelper);

        lenient().when(loanHelper.calculateEffectiveInstallmentAmount(any(BigDecimal.class), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    BigDecimal base = invocation.getArgument(0);
                    LocalDate today = invocation.getArgument(1);
                    LocalDate due = invocation.getArgument(2);
                    long daysDiff = ChronoUnit.DAYS.between(today, due);
                    BigDecimal adjustment = base.multiply(new BigDecimal("0.001"))
                            .multiply(new BigDecimal(Math.abs(daysDiff)));
                    BigDecimal effective = base;
                    if (daysDiff > 0) {
                        effective = effective.subtract(adjustment);
                    } else if (daysDiff < 0) {
                        effective = effective.add(adjustment);
                    }
                    return effective.setScale(2, RoundingMode.HALF_UP);
                });

    }

    @Test
    public void testCreateLoan_HappyPath() {
        // Prepare a valid CreateLoanRequest.
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(1L);
        request.setAmount(new BigDecimal("1000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.03"));

        // Prepare a dummy customer.
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("500")); // already used

        // Prepare a dummy loan.
        Loan loan = new Loan(customer.getId(), request.getAmount(), request.getNumberOfInstallments());
        loan.setId(10L);

        // Prepare dummy installments.
        List<LoanInstallment> installments = IntStream.range(0, request.getNumberOfInstallments())
                .mapToObj(i -> {
                    LoanInstallment li = new LoanInstallment(loan,
                            new BigDecimal("85.83"),
                            LocalDate.now().plusMonths(i + 1));
                    li.setId((long) i);
                    return li;
                })
                .collect(Collectors.toList());

        // Stub helper methods.
        doNothing().when(loanHelper).validateLoanRequest(request);
        when(loanHelper.fetchCustomerById(1L)).thenReturn(customer);
        when(loanHelper.saveLoan(any(Loan.class))).thenReturn(loan);
        doNothing().when(loanHelper).saveCustomer(any(Customer.class));
        when(loanHelper.createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments())))
                .thenReturn(installments);
        doNothing().when(loanHelper).saveInstallments(installments);

        Loan result = loanService.createLoan(request);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        // The customer's used credit limit should increase by the loan amount: 500 + 1000 = 1500.
        assertEquals(new BigDecimal("1500"), customer.getUsedCreditLimit());
        verify(loanHelper).validateLoanRequest(request);
        verify(loanHelper).fetchCustomerById(1L);
        verify(loanHelper).saveLoan(any(Loan.class));
        verify(loanHelper).saveCustomer(any(Customer.class));
        verify(loanHelper).createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments()));
        verify(loanHelper).saveInstallments(installments);
    }

    @Test
    public void testCreateLoan_InvalidInterestRate() {
        // Prepare a request with an invalid interest rate.
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(1L);
        request.setAmount(new BigDecimal("1000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.001")); // below minInterest

        doThrow(new IllegalArgumentException("Interest rate must be between " + minInterest + " and " + maxInterest))
                .when(loanHelper).validateLoanRequest(request);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> loanService.createLoan(request));
        assertEquals("Interest rate must be between " + minInterest + " and " + maxInterest, ex.getMessage());
        verify(loanHelper).validateLoanRequest(request);
    }

    @Test
    public void testCreateLoan_CreditLimitExceeded() {
        // Prepare a request where the customer's credit limit is exceeded.
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(1L);
        request.setAmount(new BigDecimal("5000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.03"));

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("1000"));

        doNothing().when(loanHelper).validateLoanRequest(request);
        when(loanHelper.fetchCustomerById(1L)).thenReturn(customer);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> loanService.createLoan(request));
        assertEquals("Customer does not have enough credit limit", ex.getMessage());
        verify(loanHelper).validateLoanRequest(request);
        verify(loanHelper).fetchCustomerById(1L);
    }

    @Test
    public void testDeleteLoan_HappyPath() {
        // Prepare a loan and associated customer.
        Loan loan = new Loan(1L, new BigDecimal("1000"), 12);
        loan.setId(10L);
        loan.setPaid(false);
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("2000"));

        when(loanHelper.fetchLoanById(10L)).thenReturn(loan);
        when(loanHelper.fetchCustomerById(1L)).thenReturn(customer);
        doNothing().when(loanHelper).saveCustomer(customer);
        doNothing().when(loanHelper).deleteLoan(loan);

        loanService.deleteLoan(10L);

        // The customer's used credit limit should be decreased by the loan amount: 2000 - 1000 = 1000.
        assertEquals(new BigDecimal("1000"), customer.getUsedCreditLimit());
        verify(loanHelper).fetchLoanById(10L);
        verify(loanHelper).fetchCustomerById(1L);
        verify(loanHelper).saveCustomer(customer);
        verify(loanHelper).deleteLoan(loan);
    }

    @Test
    public void testDeleteLoan_LoanNotFound() {
        when(loanHelper.fetchLoanById(99L)).thenThrow(new IllegalArgumentException("Loan not found with id: 99"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> loanService.deleteLoan(99L));
        assertEquals("Loan not found with id: 99", ex.getMessage());
        verify(loanHelper).fetchLoanById(99L);
    }

    @Test
    public void testPayLoan_HappyPath() {
        // Prepare a loan with 3 installments.
        Loan loan = new Loan(1L, new BigDecimal("1000"), 3);
        loan.setId(10L);
        loan.setPaid(false);

        // Create 3 installments with due dates in the past.
        List<LoanInstallment> installments = IntStream.range(0, 3)
                .mapToObj(i -> {
                    LoanInstallment li = new LoanInstallment(loan, new BigDecimal("350"), LocalDate.now().minusDays(10));
                    li.setId((long) i);
                    li.setPaid(false);
                    return li;
                })
                .collect(Collectors.toList());

        when(loanHelper.fetchLoanById(10L)).thenReturn(loan);
        when(loanHelper.findInstallmentsByLoanIdOrderByDueDateAsc(10L)).thenReturn(installments);
        doNothing().when(loanHelper).saveInstallment(any(LoanInstallment.class));


        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("1500"));


        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new BigDecimal("1050")); // This amount pays 2 installments (2 x 353.50 = 707.00)

        PaymentResponse response = loanService.payLoan(10L, paymentRequest);

        // Expect only 2 installments to be paid.
        assertNotNull(response);
        assertEquals(2, response.getInstallmentsPaid());
        // Total paid = 2 * 353.50 = 707.00
        assertEquals(new BigDecimal("707.00"), response.getTotalAmountPaid());
        // Since not all installments are paid, loanPaid should be false.
        assertFalse(response.isLoanPaid());

        verify(loanHelper).fetchLoanById(10L);
        verify(loanHelper).findInstallmentsByLoanIdOrderByDueDateAsc(10L);
        verify(loanHelper, atLeast(1)).saveInstallment(any(LoanInstallment.class));
        // Do NOT verify fetchCustomerById because it should not be invoked.
        verify(loanHelper, never()).fetchCustomerById(anyLong());
        verify(loanHelper, never()).saveLoan(loan);
        verify(loanHelper, never()).saveCustomer(any(Customer.class));
    }

    @Test
    public void testPayLoan_InsufficientPayment() {
        // Prepare a loan with 3 installments.
        Loan loan = new Loan(1L, new BigDecimal("1000"), 3);
        loan.setId(10L);
        loan.setPaid(false);

        List<LoanInstallment> installments = IntStream.range(0, 3)
                .mapToObj(i -> {
                    LoanInstallment li = new LoanInstallment(loan, new BigDecimal("350"), LocalDate.now().minusDays(10));
                    li.setId((long) i);
                    li.setPaid(false);
                    return li;
                })
                .collect(Collectors.toList());

        when(loanHelper.fetchLoanById(10L)).thenReturn(loan);
        when(loanHelper.findInstallmentsByLoanIdOrderByDueDateAsc(10L)).thenReturn(installments);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new BigDecimal("200")); // Insufficient to cover any installment

        PaymentException ex = assertThrows(PaymentException.class, () -> loanService.payLoan(10L, paymentRequest));
        assertTrue(ex.getMessage().toLowerCase().contains("insufficient"));
        verify(loanHelper).fetchLoanById(10L);
        verify(loanHelper).findInstallmentsByLoanIdOrderByDueDateAsc(10L);
    }

    @Test
    public void testCreateLoan_AsCustomerForSelf_Success() {
        // Use the default authentication (customer with id=1).
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(1L);  // customer creates loan for self
        request.setAmount(new BigDecimal("1000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.03"));

        // Dummy customer (id 1).
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("500"));

        // Dummy loan.
        Loan loan = new Loan(customer.getId(), request.getAmount(), request.getNumberOfInstallments());
        loan.setId(10L);

        // Dummy installments.
        List<LoanInstallment> installments = IntStream.range(0, request.getNumberOfInstallments())
                .mapToObj(i -> {
                    LoanInstallment li = new LoanInstallment(loan, new BigDecimal("85.83"), LocalDate.now().plusMonths(i + 1));
                    li.setId((long) i);
                    return li;
                }).collect(Collectors.toList());

        // Stub helper methods.
        doNothing().when(loanHelper).validateLoanRequest(request);
        when(loanHelper.fetchCustomerById(1L)).thenReturn(customer);
        when(loanHelper.saveLoan(any(Loan.class))).thenReturn(loan);
        doNothing().when(loanHelper).saveCustomer(any(Customer.class));
        when(loanHelper.createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments())))
                .thenReturn(installments);
        doNothing().when(loanHelper).saveInstallments(installments);

        Loan result = loanService.createLoan(request);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        // Customer's used credit should be updated: 500 + 1000 = 1500.
        assertEquals(new BigDecimal("1500"), customer.getUsedCreditLimit());
        verify(loanHelper).validateLoanRequest(request);
        verify(loanHelper).fetchCustomerById(1L);
        verify(loanHelper).saveLoan(any(Loan.class));
        verify(loanHelper).saveCustomer(any(Customer.class));
        verify(loanHelper).createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments()));
        verify(loanHelper).saveInstallments(installments);
    }

    @Test
    public void testCreateLoan_AsCustomerForOther_Failure() {
        // Default authentication is customer with id=1.
        // Attempt to create a loan for customerId 2.
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(2L);  // different customer
        request.setAmount(new BigDecimal("1000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.03"));

        // Expect an AccessDeniedException.
        assertThrows(AccessDeniedException.class, () -> loanService.createLoan(request));
        // (No further stubbing is necessary.)
    }

    @Test
    public void testCreateLoan_AsAdminForOther_Success() {
        // Change security context to an admin user.
        User adminUser = new User();
        adminUser.setId(99L);
        adminUser.setUsername("admin");
        adminUser.setPassword("encoded");
        adminUser.setRoles(Collections.singletonList(Role.ADMIN));
        CustomUserDetails adminDetails = new CustomUserDetails(adminUser);
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        // Prepare a request for a customer with id=2.
        CreateLoanRequest request = new CreateLoanRequest();
        request.setCustomerId(2L);
        request.setAmount(new BigDecimal("1000"));
        request.setNumberOfInstallments(12);
        request.setInterestRate(new BigDecimal("0.03"));

        // Dummy customer with id=2.
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setCreditLimit(new BigDecimal("5000"));
        customer.setUsedCreditLimit(new BigDecimal("500"));

        // Dummy loan.
        Loan loan = new Loan(customer.getId(), request.getAmount(), request.getNumberOfInstallments());
        loan.setId(20L);

        // Dummy installments.
        List<LoanInstallment> installments = IntStream.range(0, request.getNumberOfInstallments())
                .mapToObj(i -> {
                    LoanInstallment li = new LoanInstallment(loan, new BigDecimal("85.83"), LocalDate.now().plusMonths(i + 1));
                    li.setId((long) i);
                    return li;
                }).collect(Collectors.toList());

        // Stub helper methods.
        doNothing().when(loanHelper).validateLoanRequest(request);
        when(loanHelper.fetchCustomerById(2L)).thenReturn(customer);
        when(loanHelper.saveLoan(any(Loan.class))).thenReturn(loan);
        doNothing().when(loanHelper).saveCustomer(any(Customer.class));
        when(loanHelper.createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments())))
                .thenReturn(installments);
        doNothing().when(loanHelper).saveInstallments(installments);

        Loan result = loanService.createLoan(request);

        assertNotNull(result);
        assertEquals(20L, result.getId());
        // Customer used credit should be updated: 500 + 1000 = 1500.
        assertEquals(new BigDecimal("1500"), customer.getUsedCreditLimit());
        verify(loanHelper).validateLoanRequest(request);
        verify(loanHelper).fetchCustomerById(2L);
        verify(loanHelper).saveLoan(any(Loan.class));
        verify(loanHelper).saveCustomer(any(Customer.class));
        verify(loanHelper).createLoanInstallments(eq(loan), any(BigDecimal.class), eq(request.getNumberOfInstallments()));
        verify(loanHelper).saveInstallments(installments);
    }
}
