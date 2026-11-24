package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.ProductDto;
import com.healthdekho.entity.CustomerPolicy;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.repository.CustomerPolicyRepository;
import com.healthdekho.repository.InsuranceProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    public ProductService(InsuranceProductRepository products, CustomerPolicyRepository policies) {
        this.products = products;
        this.policies = policies;
    }
    @Transactional(readOnly = true)
    public List<ProductDto> activeProducts() {
        return products.findByActiveTrueOrderByPolicyNameAsc().stream().map(ProductService::toDto).toList();
    }
    @Transactional(readOnly = true)
    public List<ProductDto> availableProducts(String email) {
        Set<Long> existing = policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(policy -> policy.getProduct().getId()).collect(Collectors.toSet());
        return products.findByActiveTrueOrderByPolicyNameAsc().stream()
                .filter(product -> !existing.contains(product.getId())).map(ProductService::toDto).toList();
    }
    public static ProductDto toDto(InsuranceProduct product) {
        return new ProductDto(product.getProductCode(), product.getPolicyName(), product.getPolicyType(), product.getDescription(),
                product.getTermMonths(), product.getPremiumFrequency(), product.getPremiumAmount(), product.getSumInsured(),
                product.getMinEntryAge(), product.getMaxEntryAge());
    }
}
