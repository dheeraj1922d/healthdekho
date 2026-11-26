package com.healthdekho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.entity.InsuranceProduct;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.mockito.Mockito;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** No AWS needed: the Spring AI ChatClient is replaced by a mock that returns text like Bedrock would. */
class BedrockRecommenderTest {
    private final List<InsuranceProduct> eligible = List.of(TestData.healthPlan(), TestData.termPlan());
    private final RecommendationRequest input = new RecommendationRequest("health cover for my family", "1 year", "HEALTH");

    private BedrockRecommender recommenderThatAnswers(String modelAnswer) {
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class, Mockito.RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(Mockito.anyString()).call().content()).thenReturn(modelAnswer);
        return new BedrockRecommender(builder, new ObjectMapper());
    }

    @Test
    void usesTheProductsAndReasonsReturnedByTheModel() throws Exception {
        var recommender = recommenderThatAnswers(
                "[{\"code\":\"HD-HEALTH\",\"reason\":\"Fits your family\",\"suitability\":\"high\"}]");

        var result = recommender.recommend(30, input, eligible);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().product().getProductCode()).isEqualTo("HD-HEALTH");
        assertThat(result.getFirst().reason()).isEqualTo("Fits your family");
        assertThat(result.getFirst().suitability()).isEqualTo("HIGH");     // upper-cased
        assertThat(result.getFirst().fromAi()).isTrue();
    }

    @Test
    void understandsAnswersWrappedInMarkdownCodeFences() throws Exception {
        var recommender = recommenderThatAnswers(
                "```json\n[{\"code\":\"HD-TERM\",\"reason\":\"Protects income\",\"suitability\":\"MEDIUM\"}]\n```");

        assertThat(recommender.recommend(30, input, eligible)).extracting(s -> s.product().getProductCode())
                .containsExactly("HD-TERM");
    }

    @Test
    void ignoresProductCodesTheModelInvented() throws Exception {
        var recommender = recommenderThatAnswers(
                "[{\"code\":\"FAKE-123\",\"reason\":\"x\",\"suitability\":\"HIGH\"},{\"code\":\"HD-HEALTH\",\"reason\":\"y\",\"suitability\":\"HIGH\"}]");

        assertThat(recommender.recommend(30, input, eligible)).extracting(s -> s.product().getProductCode())
                .containsExactly("HD-HEALTH");
    }

    @Test
    void ignoresDuplicateCodes() throws Exception {
        var recommender = recommenderThatAnswers(
                "[{\"code\":\"HD-HEALTH\",\"reason\":\"a\"},{\"code\":\"HD-HEALTH\",\"reason\":\"b\"}]");

        assertThat(recommender.recommend(30, input, eligible)).hasSize(1);
    }

    @Test
    void unknownSuitabilityBecomesConsider() throws Exception {
        var recommender = recommenderThatAnswers("[{\"code\":\"HD-HEALTH\",\"reason\":\"a\",\"suitability\":\"AMAZING\"}]");

        assertThat(recommender.recommend(30, input, eligible).getFirst().suitability()).isEqualTo("CONSIDER");
    }

    @Test
    void emptyOrNonArrayAnswerGivesNoSuggestions() throws Exception {
        assertThat(recommenderThatAnswers("").recommend(30, input, eligible)).isEmpty();
        assertThat(recommenderThatAnswers("{\"code\":\"HD-HEALTH\"}").recommend(30, input, eligible)).isEmpty();
    }

    @Test
    void textThatIsNotJsonThrowsSoTheServiceCanFallBack() {
        var recommender = recommenderThatAnswers("Sorry, I cannot help with that.");

        assertThatThrownBy(() -> recommender.recommend(30, input, eligible)).isInstanceOf(Exception.class);
    }

    @Test
    void promptContainsTheCatalogAndPreferencesButNoPersonalDetails() throws Exception {
        var recommender = recommenderThatAnswers("[]");

        String prompt = recommender.buildPrompt(30, input, eligible);

        assertThat(prompt).contains("HD-HEALTH", "HD-TERM", "Customer age: 30", "health cover for my family");
        assertThat(prompt).doesNotContain("@");     // no email addresses are sent to the model
    }
}
