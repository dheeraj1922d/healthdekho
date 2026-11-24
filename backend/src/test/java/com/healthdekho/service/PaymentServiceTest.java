package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.PaymentLine;
import com.healthdekho.dto.ApiDtos.PaymentRequest;
import com.healthdekho.entity.PremiumInstallment;
import com.healthdekho.entity.PremiumPayment;
import com.healthdekho.repository.PremiumInstallmentRepository;
import com.healthdekho.repository.PremiumPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PremiumInstallmentRepository installments;
    @Mock PremiumPaymentRepository payments;

    @Test
    void appliesTenPercentPenaltyToOutstandingWhenGracePeriodHasPassedAndAllowsPartialPayment() {
        var service = new PaymentService(installments, payments);
        var installment = new PremiumInstallment(null, 2, LocalDate.now().minusMonths(2), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(installment, "id", 42L);
        when(installments.findLockedForCustomer(42L, "HDP-2026-0001", "aarav@example.com")).thenReturn(Optional.of(installment));
        when(payments.save(any(PremiumPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.pay("aarav@example.com", "HDP-2026-0001",
                new PaymentRequest(List.of(new PaymentLine(42L, new BigDecimal("25.00"))), "UPI"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().principalAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getFirst().penaltyAmount()).isEqualByComparingTo("10.00");
        assertThat(result.getFirst().chargedAmount()).isEqualByComparingTo("35.00");
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("25.00");
        assertThat(installment.getPaymentStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void completesInstallmentWithoutPenaltyWithinGracePeriod() {
        var service = new PaymentService(installments, payments);
        var installment = new PremiumInstallment(null, 1, LocalDate.now().minusDays(10), new BigDecimal("80.00"));
        ReflectionTestUtils.setField(installment, "id", 17L);
        when(installments.findLockedForCustomer(17L, "HDP-2026-0001", "aarav@example.com")).thenReturn(Optional.of(installment));
        when(payments.save(any(PremiumPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.pay("aarav@example.com", "HDP-2026-0001",
                new PaymentRequest(List.of(new PaymentLine(17L, new BigDecimal("80.00"))), "CARD"));

        assertThat(result.getFirst().penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("80.00");
        assertThat(installment.getPaymentStatus()).isEqualTo("PAID");
    }
}
