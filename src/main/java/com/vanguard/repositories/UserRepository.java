package com.vanguard.repositories;

import com.vanguard.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhoneNumber(String phoneNumber);

    List<User> findByUserType(String userType);

    List<User> findByUserTypeAndIsOnlineTrue(String userType);

    @Query("SELECT u FROM User u WHERE u.userType = 'DRIVER' AND u.status = 'ACTIVE' AND u.isOnline = true")
    List<User> findAvailableDrivers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.userType = :userType AND u.status = 'ACTIVE'")
    Long countActiveUsersByType(@Param("userType") String userType);

    boolean existsByPhoneNumber(String phoneNumber);
}
