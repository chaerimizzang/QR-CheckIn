
package com.qrcheckin.attendance;

import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceStatisticsService {

    private final ClassSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceStatisticsService(
            ClassSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceRepository attendanceRepository) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceStatisticsResponse getStatistics(Long courseId) {

        List<ClassSession> sessions =
                sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId);

        List<Enrollment> enrollments =
                enrollmentRepository.findByCourseId(courseId);

        List<AttendanceStatisticsResponse.SessionStatistics> sessionStats =
                new ArrayList<>();

        Map<Long, Long> absenceCounts = new HashMap<>();

        for (Enrollment enrollment : enrollments) {
            absenceCounts.put(enrollment.getUser().getId(), 0L);
        }

        for (ClassSession session : sessions) {

            if (session.getStatus() != SessionStatus.CLOSED) {
                continue;
            }

            Map<Long, AttendanceStatus> statusByUser = new HashMap<>();

            for (Attendance attendance :
                    attendanceRepository.findBySessionId(session.getId())) {
                statusByUser.put(
                        attendance.getUser().getId(),
                        attendance.getStatus()
                );
            }

            long present = 0;
            long late = 0;
            long absent = 0;
            long excused = 0;

            for (Enrollment enrollment : enrollments) {

                Long userId = enrollment.getUser().getId();

                AttendanceStatus status = statusByUser.get(userId);

                if (status == AttendanceStatus.PRESENT) {
                    present++;
                } else if (status == AttendanceStatus.LATE) {
                    late++;
                } else if (status == AttendanceStatus.EXCUSED) {
                    excused++; // 공결은 결석으로 세지 않는다
                } else {
                    // 종료된 회차에 기록이 없으면 결석으로 처리
                    absent++;
                    absenceCounts.merge(userId, 1L, Long::sum);
                }
            }

            sessionStats.add(
                    new AttendanceStatisticsResponse.SessionStatistics(
                            session.getId(),
                            session.getSessionNo(),
                            present,
                            late,
                            absent,
                            excused
                    )
            );
        }

        List<AttendanceStatisticsResponse.StudentAbsenceStatistics> topStudents =
                new ArrayList<>();

        for (Enrollment enrollment : enrollments) {

            User user = enrollment.getUser();
            long count = absenceCounts.getOrDefault(user.getId(), 0L);

            if (count > 0) {
                topStudents.add(
                        new AttendanceStatisticsResponse.StudentAbsenceStatistics(
                                user.getId(),
                                user.getStudentNo(),
                                user.getName(),
                                count
                        )
                );
            }
        }

        topStudents.sort(
                Comparator.comparingLong(
                        AttendanceStatisticsResponse.StudentAbsenceStatistics::absentCount
                ).reversed()
        );

        return new AttendanceStatisticsResponse(
                courseId,
                sessionStats,
                topStudents
        );
    }
}
