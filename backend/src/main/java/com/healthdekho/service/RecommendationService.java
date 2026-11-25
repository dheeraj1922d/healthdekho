package com.healthdekho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdekho.dto.ApiDtos.RecommendationDto;
import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.dto.ApiDtos.RecommendationResponse;
import com.healthdekho.entity.Customer;
import com.healthdekho.entity.InsuranceProduct;
import com.healthdekho.entity.RecommendationItem;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.CustomerPolicyRepository;
import com.healthdekho.repository.CustomerRepository;
import com.healthdekho.repository.InsuranceProductRepository;
import com.healthdekho.repository.RecommendationItemRepository;
import com.healthdekho.repository.RecommendationRequestRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final CustomerRepository customers;
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    private final RecommendationRequestRepository requests;
    private final RecommendationItemRepository items;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilders;
    private final ObjectMapper objectMapper;
    private final boolean aiEnabled;
    private final String modelId;

    public RecommendationService(CustomerRepository customers, InsuranceProductRepository products,
                                 CustomerPolicyRepository policies, RecommendationRequestRepository requests,
                                 RecommendationItemRepository items, ObjectProvider<ChatClient.Builder> chatClientBuilders,
                                 ObjectMapper objectMapper, @Value("${app.ai.enabled:true}") boolean aiEnabled,
                                 @Value("${spring.ai.bedrock.converse.chat.options.model:amazon.nova-lite-v1:0}") String modelId) {
        this.customers = customers;
        this.products = products;
        this.policies = policies;
        this.requests = requests;
        this.items = items;
        this.chatClientBuilders = chatClientBuilders;
        this.objectMapper = objectMapper;
        this.aiEnabled = aiEnabled;
        this.modelId = modelId;
    }

    @Transactional
    public RecommendationResponse generate(String email, RecommendationRequest input) {
        Customer customer = customers.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("Customer profile was not found."));
        int age = Period.between(customer.getDateOfBirth(), LocalDate.now()).getYears();
        Set<Long> owned = policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(policy -> policy.getProduct().getId()).collect(Collectors.toSet());
        List<InsuranceProduct> eligible = products.findByActiveTrueOrderByPolicyNameAsc().stream()
                .filter(p -> !owned.contains(p.getId()))
                .filter(p -> age >= p.getMinEntryAge() && age <= p.getMaxEntryAge())
                .toList();
        List<Suggestion> suggestions = suggest(input, age, eligible);
        com.healthdekho.entity.RecommendationRequest entity = requests.save(new com.healthdekho.entity.RecommendationRequest(customer, input.goals(),
                input.preferredTerm(), emptyToNull(input.preferredPolicyType()), age,
                suggestions.stream().anyMatch(Suggestion::fromAi) ? "bedrock-converse" : "healthdekho-ranking",
                suggestions.stream().anyMatch(Suggestion::fromAi) ? modelId : "deterministic-v1", "rec-v1"));
        List<RecommendationDto> result = new ArrayList<>();
        for (int index = 0; index < suggestions.size(); index++) {
            Suggestion suggestion = suggestions.get(index);
            items.save(new RecommendationItem(entity, suggestion.product(), index + 1, suggestion.reason(), suggestion.suitability()));
            result.add(toDto(suggestion, index + 1));
        }
        return new RecommendationResponse(entity.getId(), age,
                suggestions.stream().anyMatch(Suggestion::fromAi) ? "bedrock-converse" : "healthdekho-ranking",
                entity.getRequestedAt(), result);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> recent(String email) {
        return requests.findTop10ByCustomerEmailIgnoreCaseOrderByRequestedAtDesc(email).stream().map(request -> {
            List<RecommendationDto> result = items.findByRequestIdOrderByRankOrderAsc(request.getId()).stream()
                    .map(item -> new RecommendationDto(item.getProduct().getProductCode(), item.getProduct().getPolicyName(),
                            item.getProduct().getPolicyType(), item.getProduct().getTermMonths(), item.getProduct().getPremiumFrequency(),
                            item.getProduct().getPremiumAmount(), item.getProduct().getSumInsured(), item.getRecommendationReason(),
                            item.getSuitability(), item.getRankOrder())).toList();
            return new RecommendationResponse(request.getId(), request.getCalculatedAge(), request.getModelName(), request.getRequestedAt(), result);
        }).toList();
    }

    private List<Suggestion> suggest(RecommendationRequest input, int age, List<InsuranceProduct> eligible) {
        if (eligible.isEmpty()) return List.of();
        if (aiEnabled) {
            try {
                List<Suggestion> ai = aiSuggestions(input, age, eligible);
                if (!ai.isEmpty()) return ai;
            } catch (Exception ignored) {
                // Model outages or malformed output fall through to deterministic, database-backed ranking.
            }
        }
        String goals = Objects.toString(input.goals(), "").toLowerCase(Locale.ROOT);
        String preferredType = emptyToNull(input.preferredPolicyType());
        return eligible.stream().map(product -> {
                    int score = product.getPolicyType().equalsIgnoreCase(Objects.toString(preferredType, "")) ? 4 : 0;
                    String type = product.getPolicyType().toLowerCase(Locale.ROOT);
                    if ((goals.contains("health") || goals.contains("medical")) && type.equals("health")) score += 3;
                    if ((goals.contains("family") || goals.contains("income") || goals.contains("protect")) && type.equals("term")) score += 3;
                    if ((goals.contains("save") || goals.contains("wealth")) && type.equals("savings")) score += 3;
                    String reason = score > 0
                            ? "Recommended from your stated priorities, age eligibility, and active products in our catalog."
                            : "Eligible active product to consider; review its cover, term, and premium against your needs.";
                    return new Suggestion(product, reason, score >= 4 ? "HIGH" : score >= 2 ? "MEDIUM" : "CONSIDER", score, false);
                }).sorted(Comparator.comparingInt(Suggestion::score).reversed()
                        .thenComparing(s -> s.product().getPremiumAmount())).limit(5).toList();
    }

    private List<Suggestion> aiSuggestions(RecommendationRequest input, int age, List<InsuranceProduct> eligible) throws Exception {
        ChatClient.Builder builder = chatClientBuilders.getIfAvailable();
        if (builder == null) return List.of();
        String catalog = objectMapper.writeValueAsString(eligible.stream().map(p -> Map.of(
                "code", p.getProductCode(), "name", p.getPolicyName(), "type", p.getPolicyType(),
                "termMonths", p.getTermMonths(), "premiumFrequency", p.getPremiumFrequency(),
                "premium", p.getPremiumAmount(), "sumInsured", p.getSumInsured(), "description", p.getDescription())).toList());
        String prompt = "You are an insurance product recommender. Only recommend product codes in this catalog; never invent products. " +
                "Return JSON only: an array with up to five objects, each having code, reason (short), and suitability (HIGH, MEDIUM, or CONSIDER). " +
                "Do not make coverage guarantees. Customer age: " + age + ". Customer goals: " + Objects.toString(input.goals(), "not specified") +
                ". Preferred term: " + Objects.toString(input.preferredTerm(), "not specified") + ". Preferred type: " +
                Objects.toString(input.preferredPolicyType(), "not specified") + ". Catalog: " + catalog;
        String raw = builder.build().prompt().user(prompt).call().content();
        if (raw == null || raw.isBlank()) return List.of();
        String cleaned = raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode parsed = objectMapper.readTree(cleaned);
        if (!parsed.isArray()) return List.of();
        Map<String, InsuranceProduct> allowed = eligible.stream().collect(Collectors.toMap(InsuranceProduct::getProductCode, p -> p));
        Set<String> seen = new HashSet<>();
        List<Suggestion> response = new ArrayList<>();
        for (JsonNode item : parsed) {
            String code = item.path("code").asText("");
            InsuranceProduct product = allowed.get(code);
            if (product == null || !seen.add(code)) continue;
            String reason = item.path("reason").asText("Matches the preferences you shared.");
            String suitability = item.path("suitability").asText("CONSIDER").toUpperCase(Locale.ROOT);
            if (!Set.of("HIGH", "MEDIUM", "CONSIDER").contains(suitability)) suitability = "CONSIDER";
            response.add(new Suggestion(product, reason.substring(0, Math.min(reason.length(), 950)), suitability, 0, true));
            if (response.size() == 5) break;
        }
        return response;
    }

    private static RecommendationDto toDto(Suggestion s, int rank) {
        InsuranceProduct p = s.product();
        return new RecommendationDto(p.getProductCode(), p.getPolicyName(), p.getPolicyType(), p.getTermMonths(),
                p.getPremiumFrequency(), p.getPremiumAmount(), p.getSumInsured(), s.reason(), s.suitability(), rank);
    }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record Suggestion(InsuranceProduct product, String reason, String suitability, int score, boolean fromAi) {}
}
