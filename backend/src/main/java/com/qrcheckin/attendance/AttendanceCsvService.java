
package com.qrcheckin.attendance;

import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.course.CourseService;

@Service
public class AttendanceCsvService {

    private final AttendanceCsvRepository attendanceCsvRepository;
    private final CourseService courseService;

    public AttendanceCsvService(
            AttendanceCsvRepository attendanceCsvRepository,
            CourseService courseService) {

        this.attendanceCsvRepository = attendanceCsvRepository;
        this.courseService = courseService;
    }

    @Transactional(readOnly = true)
    public byte[] export(Long courseId, AuthUser admin) {

        // 관리자 권한 및 교육 소유 여부 확인
        courseService.getOwnedCourse(courseId, admin);

        // 해당 교육의 모든 출결 기록 조회
        List<Attendance> attendances =
                attendanceCsvRepository
                        .findBySessionCourseIdOrderBySessionSessionNoAscUserStudentNoAsc(
                                courseId);

        // 엑셀이 날짜로 바꿔서 ####### 로 보이지 않도록 요일을 넣은 글자 형식으로 쓴다.
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN);

        try (
            StringWriter writer = new StringWriter();
            CSVPrinter printer = new CSVPrinter(
                    writer,
                    CSVFormat.DEFAULT.builder()
                            .setHeader(
                                    "학번",
                                    "이름",
                                    "회차",
                                    "수업 예정일",
                                    "출결 상태",
                                    "처리 시간",
                                    "처리 방식")
                            .get())
        ) {

            for (Attendance attendance : attendances) {

                String status = switch (attendance.getStatus()) {
                    case PRESENT -> "출석";
                    case LATE -> "지각";
                    case ABSENT -> "결석";
                    case EXCUSED -> "공결";
                    default -> attendance.getStatus().name();
                };

                printer.printRecord(
                        attendance.getUser().getStudentNo(),
                        attendance.getUser().getName(),
                        attendance.getSession().getSessionNo(),
                        attendance.getSession()
                                .getScheduledAt().format(formatter),
                        status,
                        attendance.getCheckedAt() == null
                                ? "" : attendance.getCheckedAt().format(formatter),
                        sourceLabel(attendance.getSource())
                );
            }

            printer.flush();

            // 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM 추가
            return ("\uFEFF" + writer.toString())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "출결부 CSV 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    /** 처리 방식을 영어 코드 대신 한글로 보여 준다. */
    static String sourceLabel(AttendanceSource source) {
        return switch (source) {
            case QR -> "QR 출석";
            case SYSTEM -> "자동 결석";
            case MANUAL -> "교수 수정";
        };
    }
}
