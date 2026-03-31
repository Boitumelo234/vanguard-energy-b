package com.vanguard.services;

import com.vanguard.entities.PricingConfig;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.repositories.PricingConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class PricingService {

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    public Double calculatePrice(ServiceRequest request) {
        Optional<PricingConfig> configOpt = pricingConfigRepository.findByServiceTypeAndIsActiveTrue(request.getRequestType());

        if (configOpt.isEmpty()) {
            return getDefaultPrice(request);
        }

        PricingConfig config = configOpt.get();
        double total = config.getBasePrice();

        if ("FUEL_DELIVERY".equals(request.getRequestType())) {
            if (request.getFuelQuantity() != null && config.getPricePerLiter() != null) {
                total += request.getFuelQuantity() * config.getPricePerLiter();
            }
        } else if ("ROADSIDE_ASSISTANCE".equals(request.getRequestType())) {
            if ("FLAT_TIRE".equals(request.getIssueType()) && config.getFlatTireFee() != null) {
                total += config.getFlatTireFee();
            } else if ("BATTERY".equals(request.getIssueType()) && config.getBatteryServiceFee() != null) {
                total += config.getBatteryServiceFee();
            } else if ("TOWING".equals(request.getIssueType()) && config.getTowingFeePerKm() != null) {
                total += config.getTowingFeePerKm() * 10; // Default 10km towing
            }
        }

        return total;
    }

    private Double getDefaultPrice(ServiceRequest request) {
        if ("FUEL_DELIVERY".equals(request.getRequestType())) {
            return 50.0 + (request.getFuelQuantity() != null ? request.getFuelQuantity() * 15 : 0);
        } else {
            return 150.0;
        }
    }

    public PricingConfig updatePricing(PricingConfig config) {
        return pricingConfigRepository.save(config);
    }

    public Optional<PricingConfig> getPricingByServiceType(String serviceType) {
        return pricingConfigRepository.findByServiceType(serviceType);
    }
}