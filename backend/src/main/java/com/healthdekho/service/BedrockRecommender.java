package com.healthdekho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.entity.InsuranceProduct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asks Amazon Bedrock (through Spring AI's ChatClient) to pick products from the eligible catalog.
 * The model can only choose product codes that we sent it; anything else in its answer is ignored.
 */
@Component
public class BedrockRecommender {
    private static final Set<String> SUITABILITY = Set.of("HIGH", "MEDIUM", "CONSIDER");
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public BedrockRecommender(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /** Calls Bedrock. Throws if the call fails - the caller falls back to simple ranking. */
    List<Suggestion> recommend(int age, RecommendationRequest input, List<InsuranceProduct> eligible) throws Exception {
        String answer = chatClient.prompt().user(buildPrompt(age, input, eligible)).call().content();
        return parse(answer, eligible);
    }

    String buildPrompt(int age, RecommendationRequest input, List<InsuranceProduct> eligible) throws Exception {
        List<Map<String, Object>> catalog = eligible.stream().map(p -> Map.<String, Object>of(
                "code", p.getProductCode(), "name", p.getPolicyName(), "type", p.getPolicyType(),
                "termMonths", p.getTermMonths(), "premiumFrequency", p.getPremiumFrequency(),
                "premium", p.getPremiumAmount(), "sumInsured", p.getSumInsured(),
                "description", p.getDescription())).toList();
        return "You are an insurance product recommender. Only recommend product codes from the catalog below. "
                + "Reply with JSON only: an array of up to 5 objects with the fields code, reason (one short sentence) "
                + "and suitability (HIGH, MEDIUM or CONSIDER). Do not promise coverage or claim approval. "
                + "Customer age: " + age
                + ". Goals: " + Objects.toString(input.goals(), "not specified")
                + ". Preferred term: " + Objects.toString(input.preferredTerm(), "not specified")
                + ". Preferred type: " + Objects.toString(input.preferredPolicyType(), "not specified")
                + ". Catalog: " + objectMapper.writeValueAsString(catalog);
    }

    /** Turns the model's JSON text into suggestions. Unknown or duplicate codes are dropped. */
    List<Suggestion> parse(String answer, List<InsuranceProduct> eligible) throws Exception {
        if (answer == null || answer.isBlank()) return List.of();
        String json = answer.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode array = objectMapper.readTree(json);
        if (!array.isArray()) return List.of();

        Map<String, InsuranceProduct> byCode = eligible.stream()
                .collect(Collectors.toMap(InsuranceProduct::getProductCode, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<Suggestion> result = new ArrayList<>();
        for (JsonNode item : array) {
            String code = item.path("code").asText("");
            InsuranceProduct product = byCode.get(code);
            if (product == null || !seen.add(code)) continue;

            String reason = item.path("reason").asText("Matches the preferences you shared.");
            String suitability = item.path("suitability").asText("CONSIDER").toUpperCase(Locale.ROOT);
            if (!SUITABILITY.contains(suitability)) suitability = "CONSIDER";
            result.add(new Suggestion(product, reason.substring(0, Math.min(reason.length(), 950)), suitability, true));
            if (result.size() == 5) break;
        }
        return result;
    }
}
