package com.vanguard.repositories;

import com.vanguard.entities.DriverLog;
import com.vanguard.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverLogRepository extends JpaRepository<DriverLog, Long> {
    List<DriverLog> findByDriverId(Long driverId);

    List<DriverLog> findByDriverAndCreatedAtBetween(User driver, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(dl.earnings), 0) FROM DriverLog dl WHERE dl.driver = :driver AND dl.createdAt BETWEEN :start AND :end")
    Double sumEarningsByDriverBetweenDates(@Param("driver") User driver, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Optional<DriverLog> findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(User driver);
}