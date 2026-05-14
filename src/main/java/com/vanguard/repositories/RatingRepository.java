package com.vanguard.repositories;

import com.vanguard.entities.Rating;
import com.vanguard.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByRequestId(Long requestId);

    List<Rating> findByDriverOrderByCreatedAtDesc(User driver);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.driver = :driver")
    Double getAverageRatingForDriver(@Param("driver") User driver);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.driver = :driver")
    Long countRatingsForDriver(@Param("driver") User driver);

    boolean existsByRequestId(Long requestId);
}
