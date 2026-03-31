package com.vanguard.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ServiceRequestDTO {
    @NotBlank(message = "Request type is required")
    private String requestType;

    @NotBlank(message = "Pickup location is required")
    private String pickupLatitude;

    @NotBlank(message = "Pickup location is required")
    private String pickupLongitude;

    private String address;
    private String vehicleType;
    private String licensePlate;

    // For fuel delivery
    private String fuelType;
    private Double fuelQuantity;

    // For roadside
    private String issueType;

    private String notes;
}