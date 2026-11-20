package com.qrcheckin.attendance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceHistoryRepository
        extends JpaRepository<AttendanceHistory, Long> {
    List<AttendanceHistory> findByAttendanceIdOrderByModifiedAtDescIdDesc(Long attendanceId);
}