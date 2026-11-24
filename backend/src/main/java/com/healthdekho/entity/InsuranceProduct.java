package com.healthdekho.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "insurance_product")
public class InsuranceProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;
    @Column(name = "product_code", nullable = false, unique = true, length = 32)
    private String productCode;
    @Column(name = "policy_name", nullable = false, length = 160)
    private String policyName;
    @Column(name = "policy_type", nullable = false, length = 32)
    private String policyType;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "term_months", nullable = false)
    private Integer termMonths;
    @Column(name = "premium_frequency", nullable = false, length = 20)
    private String premiumFrequency;
    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;
    @Column(name = "sum_insured", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;
    @Column(name = "min_entry_age", nullable = false)
    private Integer minEntryAge;
    @Column(name = "max_entry_age", nullable = false)
    private Integer maxEntryAge;
    @Column(nullable = false)
    private boolean active;

    protected InsuranceProduct() {}
    public Long getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getPolicyName() { return policyName; }
    public String getPolicyType() { return policyType; }
    public String getDescription() { return description; }
    public Integer getTermMonths() { return termMonths; }
    public String getPremiumFrequency() { return premiumFrequency; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public Integer getMinEntryAge() { return minEntryAge; }
    public Integer getMaxEntryAge() { return maxEntryAge; }
    public boolean isActive() { return active; }
}
