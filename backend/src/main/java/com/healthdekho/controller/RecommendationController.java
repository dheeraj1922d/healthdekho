package com.healthdekho.controller;

import com.healthdekho.dto.ApiDtos.RecommendationRequest;
import com.healthdekho.dto.ApiDtos.RecommendationResponse;
import com.healthdekho.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendations;
    public RecommendationController(RecommendationService recommendations) { this.recommendations = recommendations; }
    @PostMapping
    public RecommendationResponse generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecommendationRequest request) {
        return recommendations.generate(jwt.getSubject(), request);
    }
    @GetMapping
    public List<RecommendationResponse> recent(@AuthenticationPrincipal Jwt jwt) {
        return recommendations.recent(jwt.getSubject());
    }
}
