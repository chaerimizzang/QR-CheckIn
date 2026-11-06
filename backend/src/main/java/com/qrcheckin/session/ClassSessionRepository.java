package com.qrcheckin.session;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByCourseIdOrderBySessionNoAsc(Long courseId);

    @Query("select coalesce(max(s.sessionNo), 0) from ClassSession s where s.course.id = :courseId")
    int findMaxSessionNo(@Param("courseId") Long courseId);

    void deleteByCourseId(Long courseId);

    /** 마감 스케줄러용: 마감 시각이 지난 OPEN 회차 */
    List<ClassSession> findByStatusAndClosedAtLessThanEqual(SessionStatus status, LocalDateTime now);
}
