package com.vanguard.repositories;

import com.vanguard.entities.Payment;
import com.vanguard.entities.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRequest(ServiceRequest request);

    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findByStatus(String status);
}
