package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.entity.CustomerPolicy;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    private static final String EMAIL = "a@example.com";

    @Mock CustomerRepository customers;
    @Mock InsuranceProductRepository products;
    @Mock CustomerPolicyRepository policies;
    @Mock RecommendationRequestRepository requests;
    @Mock RecommendationItemRepository items;
    @Mock BedrockRecommender bedrock;

    private final InsuranceProduct health = TestData.healthPlan();     // ages 18-65
    private final InsuranceProduct term = TestData.termPlan();         // ages 18-60
    private final InsuranceProduct savings = TestData.savingsPlan();   // ages 21-55

    @BeforeEach
    void setUp() {
        lenient().when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void givenCustomerAged(int years) {
        when(customers.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(TestData.customer(EMAIL, LocalDate.now().minusYears(years).minusDays(1))));
        when(products.findByActiveTrueOrderByPolicyNameAsc()).thenReturn(List.of(health, term, savings));
    }

    private RecommendationService service(boolean aiEnabled) {
        return new RecommendationService(customers, products, policies, requests, items, bedrock, aiEnabled, "test-model");
    }

    private static RecommendationRequest input(String goals, String type) {
        return new RecommendationRequest(goals, null, type);
    }

    @Test
    void aiDisabledUsesKeywordRankingAndNeverCallsBedrock() {
        givenCustomerAged(30);

        var response = service(false).generate(EMAIL, input("I want medical cover", "HEALTH"));

        verifyNoInteractions(bedrock);
        assertThat(response.modelName()).isEqualTo(RecommendationService.FALLBACK_SOURCE);
        assertThat(response.recommendations().getFirst().productCode()).isEqualTo("HD-HEALTH");
    }

    @Test
    void usesBedrockSuggestionsWhenAvailable() throws Exception {
        givenCustomerAged(30);
        when(bedrock.recommend(anyInt(), any(), any()))
                .thenReturn(List.of(new Suggestion(term, "Bedrock says so", "HIGH", true)));

        var response = service(true).generate(EMAIL, input("anything", null));

        assertThat(response.modelName()).isEqualTo(RecommendationService.AI_SOURCE);
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().getFirst().reason()).isEqualTo("Bedrock says so");
        verify(items).save(any());
    }

    @Test
    void fallsBackToKeywordRankingWhenBedrockFails() throws Exception {
        givenCustomerAged(30);
        when(bedrock.recommend(anyInt(), any(), any())).thenThrow(new RuntimeException("Bedrock unavailable"));

        var response = service(true).generate(EMAIL, input("save for the future", "SAVINGS"));

        assertThat(response.modelName()).isEqualTo(RecommendationService.FALLBACK_SOURCE);
        assertThat(response.recommendations().getFirst().productCode()).isEqualTo("HD-SAV");
    }

    @Test
    void fallsBackWhenBedrockReturnsNothingUseful() throws Exception {
        givenCustomerAged(30);
        when(bedrock.recommend(anyInt(), any(), any())).thenReturn(List.of());

        var response = service(true).generate(EMAIL, input("", null));

        assertThat(response.modelName()).isEqualTo(RecommendationService.FALLBACK_SOURCE);
        assertThat(response.recommendations()).isNotEmpty();
    }

    @Test
    void onlyOffersPlansMatchingTheCustomersAge() {
        givenCustomerAged(19);      // too young for savings (21+)

        var response = service(false).generate(EMAIL, input("", null));

        assertThat(response.calculatedAge()).isEqualTo(19);
        assertThat(response.recommendations()).extracting("productCode").containsExactlyInAnyOrder("HD-HEALTH", "HD-TERM");
    }

    @Test
    void doesNotOfferPlansTheCustomerAlreadyOwns() {
        givenCustomerAged(30);
        var customer = TestData.customer(EMAIL, LocalDate.of(1990, 1, 1));
        var owned = new CustomerPolicy("HDP-1", customer, health, LocalDate.now(), LocalDate.now().plusYears(1),
                "YEARLY", health.getPremiumAmount(), health.getSumInsured(), health.getPremiumAmount());
        when(policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(EMAIL)).thenReturn(List.of(owned));

        var response = service(false).generate(EMAIL, input("", null));

        assertThat(response.recommendations()).extracting("productCode").doesNotContain("HD-HEALTH");
    }

    @Test
    void returnsNothingAndSkipsBedrockWhenNoPlanIsEligible() {
        givenCustomerAged(90);

        var response = service(true).generate(EMAIL, input("", null));

        assertThat(response.recommendations()).isEmpty();
        verifyNoInteractions(bedrock);
        verify(items, never()).save(any());
    }

    // ---- the keyword ranking on its own ----

    @Test
    void preferredTypeScoresHighest() {
        var ranked = RecommendationService.rankByKeywords(input("", "TERM"), List.of(health, term, savings));

        assertThat(ranked.getFirst().product().getProductCode()).isEqualTo("HD-TERM");
        assertThat(ranked.getFirst().suitability()).isEqualTo("HIGH");
        assertThat(ranked.getFirst().fromAi()).isFalse();
    }

    @Test
    void goalKeywordsMatchPolicyTypes() {
        var ranked = RecommendationService.rankByKeywords(input("protect my family income", null), List.of(health, term, savings));

        assertThat(ranked.getFirst().product().getProductCode()).isEqualTo("HD-TERM");
        assertThat(ranked.getFirst().suitability()).isEqualTo("MEDIUM");
    }

    @Test
    void withNoPreferenceCheapestPlansComeFirst() {
        var ranked = RecommendationService.rankByKeywords(input("", null), List.of(health, term, savings));

        assertThat(ranked).extracting(s -> s.product().getProductCode()).containsExactly("HD-TERM", "HD-SAV", "HD-HEALTH");
        assertThat(ranked).allMatch(s -> s.suitability().equals("CONSIDER"));
    }
}
