package com.qrcheckin.request;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, Long> {


    @EntityGraph(attributePaths = {"session", "session.course", "student", "reviewedBy"})
    List<AttendanceRequest> findBySessionCourseOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    @EntityGraph(attributePaths = {"session", "session.course", "student", "reviewedBy"})
    List<AttendanceRequest> findByStudentIdOrderByCreatedAtDescIdDesc(Long studentId);

    boolean existsBySessionIdAndStudentIdAndStatus(Long sessionId, Long studentId, RequestStatus status);

    long countBySessionCourseOwnerIdAndStatus(Long ownerId, RequestStatus status);

    long countBySessionCourseIdAndStudentIdAndStatus(Long courseId, Long studentId, RequestStatus status);

    long countBySessionCourseIdAndStatus(Long courseId, RequestStatus status);

    List<AttendanceRequest> findBySessionId(Long sessionId);
}
