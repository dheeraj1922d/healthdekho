package com.healthdekho.repository;

import com.healthdekho.entity.RecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {
    List<RecommendationItem> findByRequestIdOrderByRankOrderAsc(Long requestId);
    @Query("select i from RecommendationItem i join i.request r join i.product p where r.id = :requestId and lower(r.customer.email) = lower(:email) and p.productCode = :productCode")
    Optional<RecommendationItem> findForRegistration(@Param("requestId") Long requestId, @Param("email") String email, @Param("productCode") String productCode);
}
