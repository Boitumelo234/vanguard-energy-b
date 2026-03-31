package com.vanguard.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LocationUpdateDTO {
    @NotBlank(message = "Latitude is required")
    private String latitude;

    @NotBlank(message = "Longitude is required")
    private String longitude;
}