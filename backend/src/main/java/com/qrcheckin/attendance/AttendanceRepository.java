package com.qrcheckin.attendance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findBySessionIdAndUserId(Long sessionId, Long userId);

    List<Attendance> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

    void deleteBySessionCourseId(Long courseId);

    void deleteBySessionCourseIdAndUserId(Long courseId, Long userId);

    List<Attendance> findByUserIdAndSessionCourseIdOrderBySessionSessionNoAsc(Long userId, Long courseId);
}
