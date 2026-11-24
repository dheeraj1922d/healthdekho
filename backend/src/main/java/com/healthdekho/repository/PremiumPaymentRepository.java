package com.healthdekho.repository;

import com.healthdekho.entity.PremiumPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PremiumPaymentRepository extends JpaRepository<PremiumPayment, Long> {
    List<PremiumPayment> findByInstallmentPolicyPolicyNumberIgnoreCaseAndInstallmentPolicyCustomerEmailIgnoreCaseOrderByPaymentDateDesc(String policyNumber, String email);
}
