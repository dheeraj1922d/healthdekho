package com.healthdekho.repository;

import com.healthdekho.entity.PremiumInstallment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PremiumInstallmentRepository extends JpaRepository<PremiumInstallment, Long> {
    List<PremiumInstallment> findByPolicyPolicyNumberIgnoreCaseAndPolicyCustomerEmailIgnoreCaseOrderByInstallmentNumberAsc(String policyNumber, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PremiumInstallment i join fetch i.policy p join fetch p.customer where i.id = :id and lower(p.policyNumber) = lower(:policyNumber) and lower(p.customer.email) = lower(:email)")
    Optional<PremiumInstallment> findLockedForCustomer(@Param("id") Long id, @Param("policyNumber") String policyNumber, @Param("email") String email);
}
