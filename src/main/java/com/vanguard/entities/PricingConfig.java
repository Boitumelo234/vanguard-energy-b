package com.vanguard.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_config")
@Data
public class PricingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_type")
    private String serviceType; // FUEL_DELIVERY, ROADSIDE_ASSISTANCE

    @Column(name = "base_price")
    private Double basePrice;

    @Column(name = "price_per_km")
    private Double pricePerKm;

    @Column(name = "price_per_liter")
    private Double pricePerLiter;

    @Column(name = "flat_tire_fee")
    private Double flatTireFee;

    @Column(name = "battery_service_fee")
    private Double batteryServiceFee;

    @Column(name = "towing_fee_per_km")
    private Double towingFeePerKm;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}