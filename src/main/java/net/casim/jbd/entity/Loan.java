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

    private Long customerId;

    private BigDecimal loanAmount;

    private int numberOfInstallments;

    private LocalDate createDate = LocalDate.now();

    private boolean isPaid = false;

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
