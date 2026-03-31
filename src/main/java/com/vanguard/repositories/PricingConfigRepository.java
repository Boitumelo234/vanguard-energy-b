package com.vanguard.repositories;

import com.vanguard.entities.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
    Optional<PricingConfig> findByServiceTypeAndIsActiveTrue(String serviceType);

    Optional<PricingConfig> findByServiceType(String serviceType);
}