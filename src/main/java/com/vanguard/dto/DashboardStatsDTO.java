package com.vanguard.dto;

import lombok.Data;
import java.util.Map;

@Data
public class DashboardStatsDTO {
    private Long totalRequests;
    private Long activeDrivers;
    private Long totalCustomers;
    private Long pendingRequests;
    private Long completedToday;
    private Double totalRevenueToday;
    private Double totalRevenueThisWeek;
    private Map<String, Long> requestsByStatus;
    private Map<String, Double> revenueByServiceType;
}
