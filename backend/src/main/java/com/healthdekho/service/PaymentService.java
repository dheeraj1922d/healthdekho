package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.PaymentDto;
import com.healthdekho.dto.ApiDtos.PaymentLine;
import com.healthdekho.dto.ApiDtos.PaymentRequest;
import com.healthdekho.entity.PremiumInstallment;
import com.healthdekho.entity.PremiumPayment;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.PremiumInstallmentRepository;
import com.healthdekho.repository.PremiumPaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.10");
    private final PremiumInstallmentRepository installments;
    private final PremiumPaymentRepository payments;
    public PaymentService(PremiumInstallmentRepository installments, PremiumPaymentRepository payments) {
        this.installments = installments;
        this.payments = payments;
    }

    @Transactional
    public List<PaymentDto> pay(String email, String policyNumber, PaymentRequest request) {
        LocalDate paymentDate = LocalDate.now();
        Set<Long> ids = new HashSet<>();
        for (PaymentLine line : request.payments()) {
            if (!ids.add(line.installmentId())) throw ApiException.badRequest("DUPLICATE_INSTALLMENT", "An installment can only be included once per payment request.");
        }
        return request.payments().stream().map(line -> payLine(email, policyNumber, request.paymentMethod(), paymentDate, line)).toList();
    }

    private PaymentDto payLine(String email, String policyNumber, String method, LocalDate paymentDate, PaymentLine line) {
        PremiumInstallment installment = installments.findLockedForCustomer(line.installmentId(), policyNumber, email)
                .orElseThrow(() -> ApiException.notFound("Installment was not found for this policy and customer."));
        BigDecimal outstanding = installment.getScheduledAmount().subtract(installment.getPaidAmount());
        if (outstanding.signum() <= 0) throw ApiException.conflict("ALREADY_PAID", "This installment has already been paid.");
        if (line.amount().compareTo(outstanding) > 0) throw ApiException.badRequest("PAYMENT_EXCEEDS_BALANCE", "Payment cannot exceed the outstanding installment amount.");
        BigDecimal penalty = paymentDate.isAfter(installment.getGracePeriodEndDate())
                ? outstanding.multiply(LATE_PENALTY_RATE).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal chargedAmount = line.amount().add(penalty);
        installment.addPayment(line.amount(), paymentDate);
        PremiumPayment payment = payments.save(new PremiumPayment(installment,
                "TXN-HD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(),
                paymentDate, chargedAmount, penalty, method));
        return PolicyService.toPaymentDto(payment);
    }
}
