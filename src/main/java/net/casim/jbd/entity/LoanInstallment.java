package net.casim.jbd.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Data
@NoArgsConstructor
public class LoanInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @JsonBackReference
    private Loan loan;

    private BigDecimal amount;

    private BigDecimal paidAmount = BigDecimal.ZERO;

    private LocalDate dueDate;

    private LocalDate paymentDate;

    private boolean isPaid = false;

    public LoanInstallment(Loan loan, BigDecimal amount, LocalDate dueDate) {
        this.loan = loan;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    public void markAsPaid(BigDecimal effectivePaidAmount, LocalDate paymentDate) {
        this.paidAmount = effectivePaidAmount;
        this.paymentDate = paymentDate;
        this.isPaid = true;
    }
}
