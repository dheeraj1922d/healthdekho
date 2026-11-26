package com.healthdekho.service;

import com.healthdekho.entity.CustomerPolicy;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.repository.CustomerPolicyRepository;
import com.healthdekho.repository.InsuranceProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock InsuranceProductRepository products;
    @Mock CustomerPolicyRepository policies;

    @Test
    void availableProductsHideThePlansTheCustomerAlreadyOwns() {
        InsuranceProduct health = TestData.healthPlan();
        InsuranceProduct term = TestData.termPlan();
        var customer = TestData.customer("a@example.com", LocalDate.of(1990, 1, 1));
        var owned = new CustomerPolicy("HDP-1", customer, health, LocalDate.now(), LocalDate.now().plusYears(1),
                "YEARLY", health.getPremiumAmount(), health.getSumInsured(), health.getPremiumAmount());
        when(products.findByActiveTrueOrderByPolicyNameAsc()).thenReturn(List.of(health, term));
        when(policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc("a@example.com")).thenReturn(List.of(owned));

        var available = new ProductService(products, policies).availableProducts("a@example.com");

        assertThat(available).extracting("productCode").containsExactly("HD-TERM");
    }
}
