package com.qrcheckin.attendance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCsvRepository
        extends JpaRepository<Attendance, Long> {

    // 특정 교육에 속한 모든 출결 기록 조회
    List<Attendance> findBySessionCourseIdOrderBySessionSessionNoAsc(
            Long courseId);
}
