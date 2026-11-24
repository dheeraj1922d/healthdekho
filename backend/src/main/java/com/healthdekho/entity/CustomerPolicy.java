package com.healthdekho.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_policy", uniqueConstraints = @UniqueConstraint(name = "uq_customer_policy_product", columnNames = {"customer_id", "product_id"}))
public class CustomerPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long id;
    @Column(name = "policy_number", nullable = false, unique = true, length = 32)
    private String policyNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;
    @Column(name = "premium_frequency", nullable = false, length = 20)
    private String premiumFrequency;
    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;
    @Column(name = "sum_insured", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;
    @Column(name = "total_premium_payable", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPremiumPayable;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CustomerPolicy() {}
    public CustomerPolicy(String policyNumber, Customer customer, InsuranceProduct product, LocalDate startDate,
                          LocalDate maturityDate, String premiumFrequency, BigDecimal premiumAmount,
                          BigDecimal sumInsured, BigDecimal totalPremiumPayable) {
        this.policyNumber = policyNumber;
        this.customer = customer;
        this.product = product;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.premiumFrequency = premiumFrequency;
        this.premiumAmount = premiumAmount;
        this.sumInsured = sumInsured;
        this.totalPremiumPayable = totalPremiumPayable;
        this.status = "ACTIVE";
        this.createdAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public Customer getCustomer() { return customer; }
    public InsuranceProduct getProduct() { return product; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public String getPremiumFrequency() { return premiumFrequency; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public BigDecimal getTotalPremiumPayable() { return totalPremiumPayable; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
