package net.casim.jbd.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Data
@NoArgsConstructor
public class Loan {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We continue to use a customerId reference. (Optionally, you could use a relationship to a Customer.)
    private Long customerId;

    // The principal amount (without interest).
    private BigDecimal loanAmount;

    // Total number of installments (allowed: 6,9,12,24).
    private int numberOfInstallments;

    // Date when the loan was created.
    private LocalDate createDate = LocalDate.now();

    // Indicates if the loan is fully paid.
    private boolean isPaid = false;

    // Link to the associated installments.
    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<LoanInstallment> installments = new ArrayList<>();

    @Transient
    private BigDecimal remainingFee = BigDecimal.ZERO;

    public Loan(Long customerId, BigDecimal loanAmount, int numberOfInstallments) {
        this.customerId = customerId;
        this.loanAmount = loanAmount;
        this.numberOfInstallments = numberOfInstallments;
    }

    public void markAsPaid() {
        this.isPaid = true;
    }
}
