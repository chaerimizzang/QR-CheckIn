package com.qrcheckin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordChangeRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash WHERE u.id = :userId")
    int updatePassword(
        @Param("userId") Long userId,
        @Param("passwordHash") String passwordHash
    );
}