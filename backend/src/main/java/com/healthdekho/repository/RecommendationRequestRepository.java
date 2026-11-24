package com.healthdekho.repository;

import com.healthdekho.entity.RecommendationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
    List<RecommendationRequest> findTop10ByCustomerEmailIgnoreCaseOrderByRequestedAtDesc(String email);
}
