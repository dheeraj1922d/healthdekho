package com.healthdekho.controller;

import com.healthdekho.dto.ApiDtos.*;
import com.healthdekho.service.PolicyService;
import com.healthdekho.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private final PolicyService service;
    private final PaymentService paymentService;
    public PolicyController(PolicyService service, PaymentService paymentService) { this.service = service; this.paymentService = paymentService; }
    @GetMapping
    public List<PolicyDto> mine(@AuthenticationPrincipal Jwt jwt) { return service.myPolicies(jwt.getSubject()); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDto register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterPolicyRequest request) {
        return service.register(jwt.getSubject(), request);
    }
    @GetMapping("/search")
    public List<PolicyDto> search(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) String policyNumber,
                                  @RequestParam(required = false) String customerName,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size) {
        return service.search(jwt.getSubject(), policyNumber, customerName, dateOfBirth, page, size);
    }
    @GetMapping("/{policyNumber}")
    public PolicyDetails details(@AuthenticationPrincipal Jwt jwt, @PathVariable String policyNumber) {
        return service.details(jwt.getSubject(), policyNumber);
    }
    @GetMapping("/{policyNumber}/installments")
    public List<InstallmentDto> installments(@AuthenticationPrincipal Jwt jwt, @PathVariable String policyNumber) {
        return service.details(jwt.getSubject(), policyNumber).installments();
    }
    @PostMapping("/{policyNumber}/payments")
    public List<PaymentDto> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable String policyNumber,
                                @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(jwt.getSubject(), policyNumber, request);
    }
}
