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
    List<ServiceRequest> findByCustomerId(Long customerId);

    List<ServiceRequest> findByDriverId(Long driverId);

    List<ServiceRequest> findByStatus(String status);

    List<ServiceRequest> findByStatusIn(List<String> statuses);

    @Query("SELECT s FROM ServiceRequest s WHERE s.driver = :driver AND s.status = 'COMPLETED' AND s.completedAt BETWEEN :startDate AND :endDate")
    List<ServiceRequest> findCompletedJobsByDriverBetweenDates(@Param("driver") User driver, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM ServiceRequest s WHERE s.status = 'COMPLETED' AND s.completedAt BETWEEN :startDate AND :endDate")
    Long countCompletedJobsBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.finalPrice), 0) FROM ServiceRequest s WHERE s.status = 'COMPLETED' AND s.completedAt BETWEEN :startDate AND :endDate")
    Double sumRevenueBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    List<ServiceRequest> findByStatusAndRequestedAtBefore(String status, LocalDateTime dateTime);

    Optional<ServiceRequest> findByIdAndDriverId(Long id, Long driverId);
}