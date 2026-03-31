package com.vanguard.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class UpdateStatusDTO {
    @NotBlank(message = "Status is required")
    private String status;

    private String notes;
    private Double finalPrice;
    private Double fuelDelivered;
}