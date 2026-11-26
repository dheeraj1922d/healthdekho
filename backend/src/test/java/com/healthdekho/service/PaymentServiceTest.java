package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.PaymentLine;
import com.healthdekho.dto.ApiDtos.PaymentRequest;
import com.healthdekho.entity.PremiumInstallment;
import com.healthdekho.entity.PremiumPayment;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.PremiumInstallmentRepository;
import com.healthdekho.repository.PremiumPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final String EMAIL = "aarav@example.com";
    private static final String POLICY = "HDP-2026-0001";

    @Mock PremiumInstallmentRepository installments;
    @Mock PremiumPaymentRepository payments;
    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(installments, payments);
    }

    /** Creates an installment due `daysAgo` days ago (grace period = 1 month after the due date). */
    private PremiumInstallment installment(long id, int daysAgo, String amount) {
        var installment = new PremiumInstallment(null, 1, LocalDate.now().minusDays(daysAgo), new BigDecimal(amount));
        ReflectionTestUtils.setField(installment, "id", id);
        return installment;
    }

    private void givenInstallmentExists(PremiumInstallment installment) {
        when(installments.findLockedForCustomer(installment.getId(), POLICY, EMAIL)).thenReturn(Optional.of(installment));
    }

    private void givenPaymentsAreSaved() {
        when(payments.save(any(PremiumPayment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private PaymentRequest pay(long installmentId, String amount) {
        return new PaymentRequest(List.of(new PaymentLine(installmentId, new BigDecimal(amount))), "UPI");
    }

    @Test
    void paymentInsideGracePeriodHasNoPenalty() {
        var installment = installment(17L, 10, "80.00");
        givenInstallmentExists(installment);
        givenPaymentsAreSaved();

        var result = service.pay(EMAIL, POLICY, pay(17L, "80.00"));

        assertThat(result.getFirst().penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getFirst().chargedAmount()).isEqualByComparingTo("80.00");
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("80.00");
        assertThat(installment.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void latePaymentAddsTenPercentPenaltyOnTheOutstandingAmount() {
        var installment = installment(42L, 60, "100.00");   // 60 days ago = grace period has ended
        givenInstallmentExists(installment);
        givenPaymentsAreSaved();

        var result = service.pay(EMAIL, POLICY, pay(42L, "25.00"));

        assertThat(result.getFirst().principalAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getFirst().penaltyAmount()).isEqualByComparingTo("10.00");   // 10% of 100.00 outstanding
        assertThat(result.getFirst().chargedAmount()).isEqualByComparingTo("35.00");
        assertThat(installment.getPaymentStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void partialPaymentInsideGracePeriodIsMarkedPartiallyPaid() {
        var installment = installment(5L, 5, "100.00");
        givenInstallmentExists(installment);
        givenPaymentsAreSaved();

        service.pay(EMAIL, POLICY, pay(5L, "40.00"));

        assertThat(installment.getPaidAmount()).isEqualByComparingTo("40.00");
        assertThat(installment.getPaymentStatus()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void rejectsPaymentLargerThanTheBalance() {
        givenInstallmentExists(installment(7L, 5, "100.00"));

        assertThatThrownBy(() -> service.pay(EMAIL, POLICY, pay(7L, "100.01")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("PAYMENT_EXCEEDS_BALANCE"));
        verify(payments, never()).save(any());
    }

    @Test
    void rejectsAnInstallmentThatIsAlreadyPaid() {
        var installment = installment(8L, 5, "100.00");
        installment.addPayment(new BigDecimal("100.00"), LocalDate.now());
        givenInstallmentExists(installment);

        assertThatThrownBy(() -> service.pay(EMAIL, POLICY, pay(8L, "10.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_PAID"));
    }

    @Test
    void rejectsTheSameInstallmentTwiceInOneRequest() {
        var request = new PaymentRequest(List.of(
                new PaymentLine(9L, new BigDecimal("10.00")), new PaymentLine(9L, new BigDecimal("10.00"))), "CARD");

        assertThatThrownBy(() -> service.pay(EMAIL, POLICY, request))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_INSTALLMENT"));
    }

    @Test
    void installmentOfAnotherCustomerIsNotFound() {
        when(installments.findLockedForCustomer(99L, POLICY, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(EMAIL, POLICY, pay(99L, "10.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
