package com.healthdekho.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "premium_installment", uniqueConstraints = @UniqueConstraint(name = "uq_policy_installment", columnNames = {"policy_id", "installment_number"}))
public class PremiumInstallment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CustomerPolicy policy;
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "grace_period_end_date", nullable = false)
    private LocalDate gracePeriodEndDate;
    @Column(name = "scheduled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal scheduledAmount;
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    protected PremiumInstallment() {}
    public PremiumInstallment(CustomerPolicy policy, int installmentNumber, LocalDate dueDate, BigDecimal amount) {
        this.policy = policy;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.gracePeriodEndDate = dueDate.plusMonths(1);
        this.scheduledAmount = amount;
        this.paidAmount = BigDecimal.ZERO;
        this.paymentStatus = "PENDING";
    }
    public void addPayment(BigDecimal principal, LocalDate paymentDate) {
        this.paidAmount = this.paidAmount.add(principal);
        this.paymentStatus = this.paidAmount.compareTo(scheduledAmount) >= 0 ? "PAID"
                : paymentDate.isAfter(gracePeriodEndDate) ? "OVERDUE" : "PARTIALLY_PAID";
    }
    public void refreshStatus(LocalDate today) {
        if (paidAmount.compareTo(scheduledAmount) >= 0) this.paymentStatus = "PAID";
        else if (today.isAfter(gracePeriodEndDate)) this.paymentStatus = "OVERDUE";
        else if (paidAmount.signum() > 0) this.paymentStatus = "PARTIALLY_PAID";
        else this.paymentStatus = "PENDING";
    }
    public Long getId() { return id; }
    public CustomerPolicy getPolicy() { return policy; }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getGracePeriodEndDate() { return gracePeriodEndDate; }
    public BigDecimal getScheduledAmount() { return scheduledAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getPaymentStatus() { return paymentStatus; }
}
