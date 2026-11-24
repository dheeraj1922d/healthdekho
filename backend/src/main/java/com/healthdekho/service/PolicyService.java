package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.*;
import com.healthdekho.entity.*;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class PolicyService {
    private final CustomerRepository customers;
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    private final PremiumInstallmentRepository installments;
    private final PremiumPaymentRepository payments;
    private final RecommendationItemRepository recommendationItems;

    public PolicyService(CustomerRepository customers, InsuranceProductRepository products,
                         CustomerPolicyRepository policies, PremiumInstallmentRepository installments,
                         PremiumPaymentRepository payments, RecommendationItemRepository recommendationItems) {
        this.customers = customers;
        this.products = products;
        this.policies = policies;
        this.installments = installments;
        this.payments = payments;
        this.recommendationItems = recommendationItems;
    }

    @Transactional(readOnly = true)
    public List<PolicyDto> myPolicies(String email) {
        return policies.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream().map(this::toDto).toList();
    }

    @Transactional
    public PolicyDto register(String email, RegisterPolicyRequest request) {
        Customer customer = customers.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("Customer profile was not found."));
        InsuranceProduct product = products.findByProductCodeAndActiveTrue(request.productCode().trim())
                .orElseThrow(() -> ApiException.notFound("This insurance product is unavailable."));
        int age = Period.between(customer.getDateOfBirth(), request.startDate()).getYears();
        if (age < product.getMinEntryAge() || age > product.getMaxEntryAge())
            throw ApiException.badRequest("AGE_NOT_ELIGIBLE", "Customer age is outside the entry-age range for this product.");
        if (policies.existsByCustomerIdAndProductId(customer.getId(), product.getId()))
            throw ApiException.conflict("DUPLICATE_POLICY", "A policy for this product is already registered to this customer.");
        RecommendationItem recommendation = request.recommendationRequestId() == null ? null
                : recommendationItems.findForRegistration(request.recommendationRequestId(), email, product.getProductCode())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_RECOMMENDATION", "This product is not part of that customer's saved recommendation."));

        int intervalMonths = frequencyMonths(product.getPremiumFrequency());
        int count = (product.getTermMonths() + intervalMonths - 1) / intervalMonths;
        BigDecimal total = product.getPremiumAmount().multiply(BigDecimal.valueOf(count));
        LocalDate maturity = request.startDate().plusMonths(product.getTermMonths());
        String policyNumber = "HDP-" + request.startDate().getYear() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CustomerPolicy policy = policies.save(new CustomerPolicy(policyNumber, customer, product, request.startDate(), maturity,
                product.getPremiumFrequency(), product.getPremiumAmount(), product.getSumInsured(), total));
        List<PremiumInstallment> schedule = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new PremiumInstallment(policy, i + 1, request.startDate().plusMonths((long) i * intervalMonths), product.getPremiumAmount()))
                .toList();
        installments.saveAll(schedule);
        if (recommendation != null) recommendation.markSelectedForRegistration();
        return toDto(policy);
    }

    @Transactional(readOnly = true)
    public PolicyDetails details(String email, String policyNumber) {
        CustomerPolicy policy = ownedPolicy(email, policyNumber);
        List<InstallmentDto> schedule = installments.findByPolicyPolicyNumberIgnoreCaseAndPolicyCustomerEmailIgnoreCaseOrderByInstallmentNumberAsc(policyNumber, email)
                .stream().map(this::toInstallmentDto).toList();
        List<PaymentDto> history = payments.findByInstallmentPolicyPolicyNumberIgnoreCaseAndInstallmentPolicyCustomerEmailIgnoreCaseOrderByPaymentDateDesc(policyNumber, email)
                .stream().map(PolicyService::toPaymentDto).toList();
        return new PolicyDetails(toDto(policy), schedule, history);
    }

    @Transactional(readOnly = true)
    public List<PolicyDto> search(String email, String policyNumber, String customerName, LocalDate dateOfBirth, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE", "Page must be non-negative and page size must be between 1 and 100.");
        if ((policyNumber == null || policyNumber.isBlank()) && (customerName == null || customerName.isBlank()) && dateOfBirth == null)
            throw ApiException.badRequest("SEARCH_REQUIRED", "Enter a policy number, customer name, or date of birth to search.");
        return policies.searchForCustomer(email, normalize(policyNumber), normalize(customerName), dateOfBirth, PageRequest.of(page, size))
                .stream().map(this::toDto).toList();
    }

    public CustomerPolicy ownedPolicy(String email, String policyNumber) {
        return policies.findByPolicyNumberIgnoreCaseAndCustomerEmailIgnoreCase(policyNumber, email)
                .orElseThrow(() -> ApiException.notFound("Policy was not found for the signed-in customer."));
    }

    public PolicyDto toDto(CustomerPolicy policy) {
        List<PremiumInstallment> schedule = installments.findByPolicyPolicyNumberIgnoreCaseAndPolicyCustomerEmailIgnoreCaseOrderByInstallmentNumberAsc(
                policy.getPolicyNumber(), policy.getCustomer().getEmail());
        BigDecimal paid = schedule.stream().map(PremiumInstallment::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        String paymentStatus = schedule.stream().allMatch(item -> item.getPaidAmount().compareTo(item.getScheduledAmount()) >= 0)
                ? "PAID" : schedule.stream().anyMatch(item -> item.getPaidAmount().compareTo(item.getScheduledAmount()) < 0 && today.isAfter(item.getGracePeriodEndDate()))
                ? "OVERDUE" : schedule.stream().anyMatch(item -> item.getPaidAmount().signum() > 0)
                ? "PARTIALLY_PAID" : "PENDING";
        return new PolicyDto(policy.getPolicyNumber(), policy.getCustomer().getFullName(), policy.getCustomer().getDateOfBirth(),
                policy.getProduct().getProductCode(), policy.getProduct().getPolicyName(), policy.getProduct().getPolicyType(),
                policy.getProduct().getTermMonths(), policy.getStartDate(), policy.getMaturityDate(),
                policy.getPremiumFrequency(), policy.getPremiumAmount(), policy.getSumInsured(), policy.getTotalPremiumPayable(),
                paid, policy.getTotalPremiumPayable().subtract(paid).max(BigDecimal.ZERO), policy.getStatus(), paymentStatus);
    }

    private InstallmentDto toInstallmentDto(PremiumInstallment installment) {
        installment.refreshStatus(LocalDate.now());
        BigDecimal remaining = installment.getScheduledAmount().subtract(installment.getPaidAmount()).max(BigDecimal.ZERO);
        return new InstallmentDto(installment.getId(), installment.getInstallmentNumber(), installment.getDueDate(),
                installment.getGracePeriodEndDate(), installment.getScheduledAmount(), installment.getPaidAmount(), remaining,
                installment.getPaymentStatus());
    }

    public static PaymentDto toPaymentDto(PremiumPayment payment) {
        return new PaymentDto(payment.getTransactionReference(), payment.getInstallment().getId(),
                payment.getInstallment().getInstallmentNumber(), payment.getPaymentDate(),
                payment.getAmountPaid().subtract(payment.getPenaltyAmount()), payment.getPenaltyAmount(),
                payment.getAmountPaid(), payment.getPaymentMethod());
    }

    private static int frequencyMonths(String frequency) {
        return switch (frequency) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "HALF_YEARLY" -> 6;
            case "YEARLY" -> 12;
            default -> throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_PRODUCT_FREQUENCY", "Product premium frequency is not configured.");
        };
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
}
