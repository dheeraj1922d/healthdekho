package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.RegisterPolicyRequest;
import com.healthdekho.entity.Customer;
import com.healthdekho.entity.CustomerPolicy;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.entity.PremiumInstallment;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {
    private static final String EMAIL = "aarav@example.com";

    @Mock CustomerRepository customers;
    @Mock InsuranceProductRepository products;
    @Mock CustomerPolicyRepository policies;
    @Mock PremiumInstallmentRepository installments;
    @Mock PremiumPaymentRepository payments;
    @Mock RecommendationItemRepository recommendationItems;
    @Captor ArgumentCaptor<List<PremiumInstallment>> savedSchedule;
    PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(customers, products, policies, installments, payments, recommendationItems);
    }

    private void givenCustomerAged(int years) {
        Customer customer = TestData.customer(EMAIL, LocalDate.now().minusYears(years).minusDays(1));
        when(customers.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(customer));
    }

    private void givenProduct(InsuranceProduct product) {
        when(products.findByProductCodeAndActiveTrue(product.getProductCode())).thenReturn(Optional.of(product));
    }

    private void givenPoliciesAreSaved() {
        when(policies.save(any(CustomerPolicy.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static RegisterPolicyRequest request(String code, LocalDate start) {
        return new RegisterPolicyRequest(code, start, null);
    }

    @Test
    void registersAMonthlyPolicyWithOneInstallmentPerMonth() {
        givenCustomerAged(30);
        givenProduct(TestData.termPlan());                 // 24 months, monthly premium of 1000
        givenPoliciesAreSaved();
        LocalDate start = LocalDate.now();

        var policy = service.register(EMAIL, request("HD-TERM", start));

        assertThat(policy.policyNumber()).startsWith("HDP-" + start.getYear());
        assertThat(policy.maturityDate()).isEqualTo(start.plusMonths(24));
        assertThat(policy.totalPremiumPayable()).isEqualByComparingTo("24000");   // 24 x 1000
        verify(installments).saveAll(savedSchedule.capture());
        assertThat(savedSchedule.getValue()).hasSize(24);
        assertThat(savedSchedule.getValue().getFirst().getDueDate()).isEqualTo(start);
        assertThat(savedSchedule.getValue().get(1).getDueDate()).isEqualTo(start.plusMonths(1));
    }

    @Test
    void quarterlyPolicyHasOneInstallmentEveryThreeMonths() {
        givenCustomerAged(30);
        givenProduct(TestData.savingsPlan());              // 120 months, quarterly
        givenPoliciesAreSaved();

        service.register(EMAIL, request("HD-SAV", LocalDate.now()));

        verify(installments).saveAll(savedSchedule.capture());
        assertThat(savedSchedule.getValue()).hasSize(40);  // 120 / 3
    }

    @Test
    void customerYoungerThanTheMinimumEntryAgeIsRejected() {
        givenCustomerAged(19);
        givenProduct(TestData.savingsPlan());              // minimum age 21

        assertThatThrownBy(() -> service.register(EMAIL, request("HD-SAV", LocalDate.now())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AGE_NOT_ELIGIBLE"));
        verify(policies, never()).save(any());
    }

    @Test
    void customerOlderThanTheMaximumEntryAgeIsRejected() {
        givenCustomerAged(70);
        givenProduct(TestData.healthPlan());               // maximum age 65

        assertThatThrownBy(() -> service.register(EMAIL, request("HD-HEALTH", LocalDate.now())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AGE_NOT_ELIGIBLE"));
    }

    @Test
    void sameProductCannotBeRegisteredTwice() {
        givenCustomerAged(30);
        var product = TestData.healthPlan();
        givenProduct(product);
        when(policies.existsByCustomerIdAndProductId(1L, product.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.register(EMAIL, request("HD-HEALTH", LocalDate.now())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_POLICY"));
    }

    @Test
    void unknownOrInactiveProductIsNotFound() {
        givenCustomerAged(30);
        when(products.findByProductCodeAndActiveTrue("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(EMAIL, request("NOPE", LocalDate.now())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void searchNeedsAtLeastOneCriterion() {
        assertThatThrownBy(() -> service.search(EMAIL, " ", null, null, 0, 25))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("SEARCH_REQUIRED"));
    }

    @Test
    void searchRejectsAnInvalidPageSize() {
        assertThatThrownBy(() -> service.search(EMAIL, "HDP", null, null, 0, 101))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_PAGE"));
        assertThatThrownBy(() -> service.search(EMAIL, "HDP", null, null, -1, 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void policyOfAnotherCustomerIsNotFound() {
        when(policies.findByPolicyNumberIgnoreCaseAndCustomerEmailIgnoreCase("HDP-OTHER", EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(EMAIL, "HDP-OTHER"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
