package com.vanguard.dto;

        import lombok.Data;
        import java.time.LocalDateTime;

@Data
public class JobResponseDTO {
    private Long id;
    private String requestType;
    private String status;
    private String pickupLatitude;
    private String pickupLongitude;
    private String address;
    private String customerName;
    private String customerPhone;
    private String vehicleType;
    private String issueType;
    private Double estimatedPrice;
    private LocalDateTime requestedAt;
}
