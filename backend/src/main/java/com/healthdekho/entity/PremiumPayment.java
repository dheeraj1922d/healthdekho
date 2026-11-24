package com.healthdekho.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "premium_payment")
public class PremiumPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private PremiumInstallment installment;
    @Column(name = "transaction_reference", nullable = false, unique = true, length = 48)
    private String transactionReference;
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;
    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;
    @Column(name = "penalty_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount;
    @Column(name = "payment_method", nullable = false, length = 24)
    private String paymentMethod;

    protected PremiumPayment() {}
    public PremiumPayment(PremiumInstallment installment, String reference, LocalDate paymentDate,
                          BigDecimal amountPaid, BigDecimal penaltyAmount, String paymentMethod) {
        this.installment = installment;
        this.transactionReference = reference;
        this.paymentDate = paymentDate;
        this.amountPaid = amountPaid;
        this.penaltyAmount = penaltyAmount;
        this.paymentMethod = paymentMethod;
    }
    public Long getId() { return id; }
    public PremiumInstallment getInstallment() { return installment; }
    public String getTransactionReference() { return transactionReference; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public String getPaymentMethod() { return paymentMethod; }
}
