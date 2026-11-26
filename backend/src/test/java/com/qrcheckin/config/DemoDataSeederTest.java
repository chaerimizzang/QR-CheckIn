package com.qrcheckin.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.request.AttendanceRequestRepository;
import com.qrcheckin.request.RequestStatus;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.user.UserRepository;

/** 시연용 더미 데이터가 오류 없이 들어가는지(H2). 실제 실행과 달리 끝나도 프로그램을 끄지 않는다 */
@SpringBootTest
@ActiveProfiles({"local", "demo-seed"})
@TestPropertySource(properties = {"app.demo.exit-after=false",
        "spring.datasource.url=jdbc:h2:mem:demoseed;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class DemoDataSeederTest {

    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired ClassSessionRepository sessions;
    @Autowired AttendanceRepository attendances;
    @Autowired AttendanceRequestRepository requests;

    @Test
    void 시연_데이터가_들어간다() {
        assertThat(users.count()).isEqualTo(32);
        assertThat(courses.count()).isEqualTo(3);
        // 14주: 소프트웨어공학 14 + 자료구조 28 + 데이터베이스 14
        assertThat(sessions.count()).isEqualTo(56);
        assertThat(attendances.count()).isPositive();
        assertThat(users.findByEmail("student01@demo.kr")).isPresent();
        assertThat(requests.findAll()).extracting(r -> r.getStatus()).contains(RequestStatus.APPROVED);
    }
}
