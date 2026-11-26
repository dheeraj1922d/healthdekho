package com.healthdekho.service;

import com.healthdekho.entity.Customer;
import com.healthdekho.entity.InsuranceProduct;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Builds sample entities for the tests. (Entities have no setters, so we set fields directly.) */
final class TestData {
    private TestData() {}

    static Customer customer(String email, LocalDate dateOfBirth) {
        Customer c = newInstance(Customer.class);
        ReflectionTestUtils.setField(c, "id", 1L);
        ReflectionTestUtils.setField(c, "email", email);
        ReflectionTestUtils.setField(c, "fullName", "Test Customer");
        ReflectionTestUtils.setField(c, "dateOfBirth", dateOfBirth);
        ReflectionTestUtils.setField(c, "phone", "9999999999");
        ReflectionTestUtils.setField(c, "status", "ACTIVE");
        return c;
    }

    static InsuranceProduct product(long id, String code, String type, String frequency, int termMonths,
                                    String premium, int minAge, int maxAge) {
        InsuranceProduct p = newInstance(InsuranceProduct.class);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "productCode", code);
        ReflectionTestUtils.setField(p, "policyName", "Plan " + code);
        ReflectionTestUtils.setField(p, "policyType", type);
        ReflectionTestUtils.setField(p, "description", "Description of " + code);
        ReflectionTestUtils.setField(p, "termMonths", termMonths);
        ReflectionTestUtils.setField(p, "premiumFrequency", frequency);
        ReflectionTestUtils.setField(p, "premiumAmount", new BigDecimal(premium));
        ReflectionTestUtils.setField(p, "sumInsured", new BigDecimal("1000000"));
        ReflectionTestUtils.setField(p, "minEntryAge", minAge);
        ReflectionTestUtils.setField(p, "maxEntryAge", maxAge);
        ReflectionTestUtils.setField(p, "active", true);
        return p;
    }

    /** Health plan, yearly premium, 1 year term, ages 18-65. */
    static InsuranceProduct healthPlan() { return product(1, "HD-HEALTH", "HEALTH", "YEARLY", 12, "18000", 18, 65); }
    /** Term plan, monthly premium, 2 year term (= 24 installments), ages 18-60. */
    static InsuranceProduct termPlan() { return product(2, "HD-TERM", "TERM", "MONTHLY", 24, "1000", 18, 60); }
    /** Savings plan, quarterly premium, ages 21-55. */
    static InsuranceProduct savingsPlan() { return product(3, "HD-SAV", "SAVINGS", "QUARTERLY", 120, "7500", 21, 55); }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
