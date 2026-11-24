package com.healthdekho.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_recommendation_item", uniqueConstraints = @UniqueConstraint(name = "uq_recommendation_product", columnNames = {"recommendation_request_id", "product_id"}))
public class RecommendationItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_item_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_request_id", nullable = false)
    private RecommendationRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;
    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;
    @Column(name = "recommendation_reason", nullable = false, length = 1000)
    private String recommendationReason;
    @Column(nullable = false, length = 20)
    private String suitability;
    @Column(name = "selected_for_registration", nullable = false)
    private boolean selectedForRegistration;

    protected RecommendationItem() {}
    public RecommendationItem(RecommendationRequest request, InsuranceProduct product, int rankOrder,
                              String recommendationReason, String suitability) {
        this.request = request;
        this.product = product;
        this.rankOrder = rankOrder;
        this.recommendationReason = recommendationReason;
        this.suitability = suitability;
        this.selectedForRegistration = false;
    }
    public Long getId() { return id; }
    public RecommendationRequest getRequest() { return request; }
    public InsuranceProduct getProduct() { return product; }
    public Integer getRankOrder() { return rankOrder; }
    public String getRecommendationReason() { return recommendationReason; }
    public String getSuitability() { return suitability; }
    public boolean isSelectedForRegistration() { return selectedForRegistration; }
    public void markSelectedForRegistration() { this.selectedForRegistration = true; }
}
