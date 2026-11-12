package com.qrcheckin.attendance;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import com.qrcheckin.auth.AuthUser;

@Service
public class AttendanceRateCsvService {

    private final AttendanceRateService attendanceRateService;

    public AttendanceRateCsvService(
            AttendanceRateService attendanceRateService) {
        this.attendanceRateService = attendanceRateService;
    }

    public byte[] export(Long courseId, AuthUser admin) {

        // 기존 출석률 계산 기능 재사용
        AttendanceRateResponse result =
                attendanceRateService.calculate(courseId, admin);

        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(
                     writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(
                                     "학번",
                                     "이름",
                                     "총 회차",
                                     "출석",
                                     "지각",
                                     "결석",
                                     "환산 결석",
                                     "출석률"
                             )
                             .get())) {

            for (AttendanceRateResponse.StudentRate student
                    : result.students()) {

                printer.printRecord(
                        student.studentNo(),
                        student.name(),
                        result.totalSessions(),
                        student.presentCount(),
                        student.lateCount(),
                        student.absentCount(),
                        student.convertedAbsences(),
                        student.attendanceRate() + "%"
                );
            }

            printer.flush();

            // 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM 추가
            return ("\uFEFF" + writer.toString())
                    .getBytes(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "출석률 CSV 파일 생성 중 오류가 발생했습니다.", e);
        }
    }
}
