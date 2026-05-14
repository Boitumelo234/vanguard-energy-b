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

    Optional<DriverLog> findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(User driver);

    @Query("SELECT COALESCE(SUM(dl.earnings), 0) FROM DriverLog dl WHERE dl.driver = :driver AND dl.shiftStart BETWEEN :startDate AND :endDate")
    Double sumEarningsByDriverBetweenDates(@Param("driver") User driver,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT dl FROM DriverLog dl WHERE dl.driver = :driver ORDER BY dl.shiftStart DESC")
    List<DriverLog> findAllByDriverOrderByShiftStartDesc(@Param("driver") User driver);

    @Query("SELECT COALESCE(SUM(dl.jobsCompleted), 0) FROM DriverLog dl WHERE dl.driver = :driver AND dl.shiftStart BETWEEN :startDate AND :endDate")
    Long sumJobsCompletedByDriverBetweenDates(@Param("driver") User driver,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(AVG(dl.rating), 0) FROM DriverLog dl WHERE dl.driver = :driver AND dl.rating IS NOT NULL")
    Double getAverageRatingForDriver(@Param("driver") User driver);
}
