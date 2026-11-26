package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.RecommendationDto;
import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.dto.ApiDtos.RecommendationResponse;
import com.healthdekho.entity.Customer;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.entity.RecommendationItem;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommends products the customer is old enough for and does not already own.
 * Tries Bedrock first; if AI is switched off or fails, uses a simple keyword ranking so the page always works.
 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    static final String AI_SOURCE = "bedrock-converse";
    static final String FALLBACK_SOURCE = "healthdekho-ranking";

    private final CustomerRepository customers;
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    private final RecommendationRequestRepository requests;
    private final RecommendationItemRepository items;
    private final BedrockRecommender bedrock;
    private final boolean aiEnabled;
    private final String modelId;

    public RecommendationService(CustomerRepository customers, InsuranceProductRepository products,
                                 CustomerPolicyRepository policies, RecommendationRequestRepository requests,
                                 RecommendationItemRepository items, BedrockRecommender bedrock,
                                 @Value("${app.ai-enabled:true}") boolean aiEnabled,
                                 @Value("${spring.ai.bedrock.converse.chat.options.model:amazon.nova-lite-v1:0}") String modelId) {
        this.customers = customers;
        this.products = products;
        this.policies = policies;
        this.requests = requests;
        this.items = items;
        this.bedrock = bedrock;
        this.aiEnabled = aiEnabled;
        this.modelId = modelId;
    }

    @Transactional
    public RecommendationResponse generate(String email, RecommendationRequest input) {
        Customer customer = customers.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("Customer profile was not found."));
        int age = Period.between(customer.getDateOfBirth(), LocalDate.now()).getYears();

        List<Suggestion> suggestions = suggest(input, age, eligibleProducts(email, age));
        boolean fromAi = suggestions.stream().anyMatch(Suggestion::fromAi);

        var saved = requests.save(new com.healthdekho.entity.RecommendationRequest(customer, input.goals(),
                input.preferredTerm(), emptyToNull(input.preferredPolicyType()), age,
                fromAi ? AI_SOURCE : FALLBACK_SOURCE, fromAi ? modelId : "keyword-ranking-v1", "rec-v1"));

        List<RecommendationDto> result = new ArrayList<>();
        for (int i = 0; i < suggestions.size(); i++) {
            Suggestion s = suggestions.get(i);
            items.save(new RecommendationItem(saved, s.product(), i + 1, s.reason(), s.suitability()));
            result.add(toDto(s.product(), s.reason(), s.suitability(), i + 1));
        }
        return new RecommendationResponse(saved.getId(), age, saved.getModelName(), saved.getRequestedAt(), result);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> recent(String email) {
        return requests.findTop10ByCustomerEmailIgnoreCaseOrderByRequestedAtDesc(email).stream().map(request -> {
            List<RecommendationDto> result = items.findByRequestIdOrderByRankOrderAsc(request.getId()).stream()
                    .map(i -> toDto(i.getProduct(), i.getRecommendationReason(), i.getSuitability(), i.getRankOrder()))
                    .toList();
            return new RecommendationResponse(request.getId(), request.getCalculatedAge(), request.getModelName(),
                    request.getRequestedAt(), result);
        }).toList();
    }

    /** Active products, old/young enough for the customer, that they do not already own. */
    private List<InsuranceProduct> eligibleProducts(String email, int age) {
        Set<Long> owned = policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(p -> p.getProduct().getId()).collect(Collectors.toSet());
        return products.findByActiveTrueOrderByPolicyNameAsc().stream()
                .filter(p -> !owned.contains(p.getId()))
                .filter(p -> age >= p.getMinEntryAge() && age <= p.getMaxEntryAge())
                .toList();
    }

    private List<Suggestion> suggest(RecommendationRequest input, int age, List<InsuranceProduct> eligible) {
        if (eligible.isEmpty()) return List.of();
        if (aiEnabled) {
            try {
                List<Suggestion> fromBedrock = bedrock.recommend(age, input, eligible);
                if (!fromBedrock.isEmpty()) return fromBedrock;
            } catch (Exception e) {
                log.warn("Bedrock recommendation failed, using catalog ranking instead: {}", e.toString());
            }
        }
        return rankByKeywords(input, eligible);
    }

    /** Simple scoring: +4 for the preferred type, +3 when the goals text mentions a matching keyword. */
    static List<Suggestion> rankByKeywords(RecommendationRequest input, List<InsuranceProduct> eligible) {
        String goals = Objects.toString(input.goals(), "").toLowerCase(Locale.ROOT);
        String preferredType = Objects.toString(emptyToNull(input.preferredPolicyType()), "");

        record Scored(InsuranceProduct product, int score) {}
        return eligible.stream().map(p -> {
                    String type = p.getPolicyType().toLowerCase(Locale.ROOT);
                    int score = p.getPolicyType().equalsIgnoreCase(preferredType) ? 4 : 0;
                    if (type.equals("health") && containsAny(goals, "health", "medical")) score += 3;
                    if (type.equals("term") && containsAny(goals, "family", "income", "protect")) score += 3;
                    if (type.equals("savings") && containsAny(goals, "save", "wealth")) score += 3;
                    return new Scored(p, score);
                })
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.product().getPremiumAmount()))
                .limit(5)
                .map(s -> new Suggestion(s.product(),
                        s.score() > 0 ? "Matches your stated priorities and age eligibility."
                                      : "Eligible plan to consider - compare its cover, term and premium.",
                        s.score() >= 4 ? "HIGH" : s.score() >= 2 ? "MEDIUM" : "CONSIDER", false))
                .toList();
    }

    private static boolean containsAny(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    private static RecommendationDto toDto(InsuranceProduct p, String reason, String suitability, int rank) {
        return new RecommendationDto(p.getProductCode(), p.getPolicyName(), p.getPolicyType(), p.getTermMonths(),
                p.getPremiumFrequency(), p.getPremiumAmount(), p.getSumInsured(), reason, suitability, rank);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
