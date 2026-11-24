package com.healthdekho.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record CustomerDto(Long id, String email, String fullName, LocalDate dateOfBirth, String phone) {}
    public record LoginResponse(String token, String tokenType, long expiresInSeconds, CustomerDto customer) {}
    public record ProductDto(String productCode, String policyName, String policyType, String description,
                             int termMonths, String premiumFrequency, BigDecimal premiumAmount,
                             BigDecimal sumInsured, int minEntryAge, int maxEntryAge) {}
    public record RegisterPolicyRequest(@NotBlank String productCode, @NotNull @PastOrPresent LocalDate startDate,
                                        Long recommendationRequestId) {}
    public record PolicyDto(String policyNumber, String customerName, LocalDate dateOfBirth,
                            String productCode, String policyName, String policyType,
                            int termMonths, LocalDate startDate, LocalDate maturityDate, String premiumFrequency,
                            BigDecimal premiumAmount, BigDecimal sumInsured, BigDecimal totalPremiumPayable,
                            BigDecimal totalPremiumPaid, BigDecimal remainingPremium, String status, String paymentStatus) {}
    public record InstallmentDto(Long id, int installmentNumber, LocalDate dueDate, LocalDate gracePeriodEndDate,
                                 BigDecimal scheduledAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                                 String paymentStatus) {}
    public record PaymentLine(@NotNull Long installmentId, @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
    public record PaymentRequest(@NotEmpty List<@Valid PaymentLine> payments,
                                 @NotBlank @Pattern(regexp = "UPI|CARD|NET_BANKING|BANK_TRANSFER", message = "Choose a supported payment method") String paymentMethod) {}
    public record PaymentDto(String transactionReference, Long installmentId, int installmentNumber,
                             LocalDate paymentDate, BigDecimal principalAmount, BigDecimal penaltyAmount,
                             BigDecimal chargedAmount, String paymentMethod) {}
    public record PolicyDetails(PolicyDto policy, List<InstallmentDto> installments, List<PaymentDto> paymentHistory) {}
    public record RecommendationRequest(@Size(max = 1000) String goals,
                                        @Size(max = 120) String preferredTerm,
                                        @Pattern(regexp = "|TERM|HEALTH|SAVINGS|ACCIDENT", message = "Choose a supported policy type") String preferredPolicyType) {}
    public record RecommendationDto(String productCode, String policyName, String policyType, int termMonths,
                                    String premiumFrequency, BigDecimal premiumAmount, BigDecimal sumInsured,
                                    String reason, String suitability, int rank) {}
    public record RecommendationResponse(Long requestId, int calculatedAge, String modelName,
                                         OffsetDateTime requestedAt, List<RecommendationDto> recommendations) {}
    public record ErrorResponse(String message, String code, OffsetDateTime timestamp) {}
}
