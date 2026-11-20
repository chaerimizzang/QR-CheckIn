package com.qrcheckin.attendance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findBySessionIdAndUserId(Long sessionId, Long userId);

    /** D-12 같은 회차에 이 기기로 이미 출석한 기록 */
    Optional<Attendance> findFirstBySessionIdAndDeviceId(Long sessionId, String deviceId);

    List<Attendance> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

    void deleteBySessionCourseId(Long courseId);

    void deleteBySessionCourseIdAndUserId(Long courseId, Long userId);

    List<Attendance> findByUserIdAndSessionCourseIdOrderBySessionSessionNoAsc(Long userId, Long courseId);
}
