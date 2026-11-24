package com.healthdekho.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_recommendation_request")
public class RecommendationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_request_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(name = "insurance_goals", length = 1000)
    private String insuranceGoals;
    @Column(name = "preferred_term", length = 120)
    private String preferredTerm;
    @Column(name = "preferred_policy_type", length = 32)
    private String preferredPolicyType;
    @Column(name = "calculated_age", nullable = false)
    private Integer calculatedAge;
    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;
    @Column(name = "model_version", nullable = false, length = 120)
    private String modelVersion;
    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    protected RecommendationRequest() {}
    public RecommendationRequest(Customer customer, String goals, String preferredTerm, String preferredType,
                                 int age, String modelName, String modelVersion, String promptVersion) {
        this.customer = customer;
        this.insuranceGoals = goals;
        this.preferredTerm = preferredTerm;
        this.preferredPolicyType = preferredType;
        this.calculatedAge = age;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.requestedAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getInsuranceGoals() { return insuranceGoals; }
    public String getPreferredTerm() { return preferredTerm; }
    public String getPreferredPolicyType() { return preferredPolicyType; }
    public Integer getCalculatedAge() { return calculatedAge; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
}
