package com.vanguard.repositories;

import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    List<ServiceRequest> findByCustomerIdOrderByRequestedAtDesc(Long customerId);

    // Keep old name for backward compat
    default List<ServiceRequest> findByCustomerId(Long customerId) {
        return findByCustomerIdOrderByRequestedAtDesc(customerId);
    }

    List<ServiceRequest> findByDriverIdOrderByRequestedAtDesc(Long driverId);

    default List<ServiceRequest> findByDriverId(Long driverId) {
        return findByDriverIdOrderByRequestedAtDesc(driverId);
    }

    List<ServiceRequest> findByStatus(String status);

    List<ServiceRequest> findByStatusInOrderByRequestedAtAsc(List<String> statuses);

    @Query("SELECT s FROM ServiceRequest s WHERE s.driver = :driver AND s.status = 'COMPLETED' " +
            "AND s.completedAt BETWEEN :startDate AND :endDate")
    List<ServiceRequest> findCompletedJobsByDriverBetweenDates(@Param("driver") User driver,
                                                               @Param("startDate") LocalDateTime startDate,
                                                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM ServiceRequest s WHERE s.status = 'COMPLETED' " +
            "AND s.completedAt BETWEEN :startDate AND :endDate")
    Long countCompletedJobsBetweenDates(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.finalPrice), 0) FROM ServiceRequest s WHERE s.status = 'COMPLETED' " +
            "AND s.completedAt BETWEEN :startDate AND :endDate")
    Double sumRevenueBetweenDates(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    // Active (non-terminal) requests for a customer — for tracking view
    @Query("SELECT s FROM ServiceRequest s WHERE s.customer.id = :customerId " +
            "AND s.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY s.requestedAt DESC")
    List<ServiceRequest> findActiveRequestsByCustomer(@Param("customerId") Long customerId);

    // Pending requests older than X minutes — for auto-notify
    List<ServiceRequest> findByStatusAndRequestedAtBefore(String status, LocalDateTime dateTime);

    Optional<ServiceRequest> findByIdAndDriverId(Long id, Long driverId);

    // Revenue breakdown by type for admin analytics
    @Query("SELECT s.requestType, COALESCE(SUM(s.finalPrice), 0) FROM ServiceRequest s " +
            "WHERE s.status = 'COMPLETED' AND s.completedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY s.requestType")
    List<Object[]> sumRevenueByTypeAndDateRange(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM ServiceRequest s WHERE s.driver.id = :driverId AND s.status = 'ACCEPTED'")
    long countActiveJobsForDriver(@Param("driverId") Long driverId);
}
