package com.healthdekho.repository;

import com.healthdekho.entity.InsuranceProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {
    List<InsuranceProduct> findByActiveTrueOrderByPolicyNameAsc();
    Optional<InsuranceProduct> findByProductCodeAndActiveTrue(String productCode);
}
