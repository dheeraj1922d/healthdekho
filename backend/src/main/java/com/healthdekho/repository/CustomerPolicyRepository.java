package com.healthdekho.repository;

import com.healthdekho.entity.CustomerPolicy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerPolicyRepository extends JpaRepository<CustomerPolicy, Long> {
    List<CustomerPolicy> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);
    Optional<CustomerPolicy> findByPolicyNumberIgnoreCaseAndCustomerEmailIgnoreCase(String policyNumber, String email);
    @Query("select p from CustomerPolicy p join fetch p.customer join fetch p.product " +
           "where lower(p.customer.email) = lower(:email) " +
           "and (:policy = '' or lower(p.policyNumber) like lower(concat('%', :policy, '%'))) " +
           "and (:name = '' or lower(p.customer.fullName) like lower(concat('%', :name, '%'))) " +
           "and (cast(:dob as date) is null or p.customer.dateOfBirth = :dob) order by p.createdAt desc")
    List<CustomerPolicy> searchForCustomer(@Param("email") String email, @Param("policy") String policy,
                                           @Param("name") String name, @Param("dob") LocalDate dob, Pageable pageable);
}
