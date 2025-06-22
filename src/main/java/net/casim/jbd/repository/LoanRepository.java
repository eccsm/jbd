package net.casim.jbd.repository;

import net.casim.jbd.entity.Loan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @EntityGraph(attributePaths = {"installments"})
    List<Loan> findByCustomerId(Long customerId);
    
    @EntityGraph(attributePaths = {"installments"})
    List<Loan> findByCustomerIdAndIsPaid(Long customerId, boolean isPaid);
    
    @EntityGraph(attributePaths = {"installments"})
    List<Loan> findByCustomerIdAndNumberOfInstallment(Long customerId, int numberOfInstallment);
    
    @EntityGraph(attributePaths = {"installments"})
    List<Loan> findByCustomerIdAndIsPaidAndNumberOfInstallment(
            Long customerId, boolean isPaid, int numberOfInstallment);
    
    @EntityGraph(attributePaths = {"installments"})
    @Query("SELECT l FROM Loan l WHERE l.customerId = :customerId AND "
            + "(:startDate IS NULL OR l.createDate >= :startDate) AND "
            + "(:endDate IS NULL OR l.createDate <= :endDate)")
    List<Loan> findByCustomerIdAndDateRange(
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
