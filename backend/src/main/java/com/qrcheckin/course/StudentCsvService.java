
package com.qrcheckin.course;

import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;

@Service
public class StudentCsvService {

    private final CourseService courseService;
    private final StudentCsvUserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentContactRepository contactRepository;

    public StudentCsvService(
            CourseService courseService,
            StudentCsvUserRepository userRepository,
            EnrollmentRepository enrollmentRepository,
            StudentContactRepository contactRepository) {

        this.courseService = courseService;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.contactRepository = contactRepository;
    }

    @Transactional
    public StudentCsvResult upload(
            Long courseId, MultipartFile file, AuthUser admin) {

        // 관리자 권한 및 교육 소유 여부 확인
        Course course = courseService.getOwnedCourse(courseId, admin);

        List<StudentCsvResult.FailedStudent> failures = new ArrayList<>();
        Set<String> processedNumbers = new HashSet<>();
        int successCount = 0;

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
        }

        try (
            InputStreamReader inputReader = new InputStreamReader(
                    file.getInputStream(), StandardCharsets.UTF_8);

            PushbackReader reader = new PushbackReader(inputReader, 1)
        ) {

            // CSV 파일 맨 앞의 UTF-8 BOM 제거
            int firstChar = reader.read();

            if (firstChar != '\uFEFF' && firstChar != -1) {
                reader.unread(firstChar);
            }

            try (CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .get()
                    .parse(reader)) {

                // CSV 필수 열 확인
                if (!parser.getHeaderMap().keySet().containsAll(
                        List.of("학번", "이름", "연락처"))) {
                    throw new IllegalArgumentException(
                            "CSV 첫 줄에는 학번,이름,연락처가 필요합니다.");
                }

                for (CSVRecord record : parser) {

                    int row = (int) record.getRecordNumber() + 1;
                    String studentNo = "";

                    try {
                        studentNo = record.get("학번").trim();
                        String name = record.get("이름").trim();
                        String phone = record.get("연락처").trim();

                        // 필수 항목 검사
                        if (studentNo.isEmpty()
                                || name.isEmpty()
                                || phone.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "필수 항목이 비어 있습니다.");
                        }

                        // 연락처 형식 검사
                        if (!phone.matches(
                                "01[016789]-?\\d{3,4}-?\\d{4}")) {
                            throw new IllegalArgumentException(
                                    "연락처 형식이 올바르지 않습니다.");
                        }

                        // CSV 파일 내부 중복 학번 검사
                        if (!processedNumbers.add(studentNo)) {
                            throw new IllegalArgumentException(
                                    "CSV 파일 안에 중복된 학번입니다.");
                        }

                        // 학번으로 기존 회원 검색
                        List<User> users =
                                userRepository.findByStudentNo(studentNo);

                        if (users.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "가입된 학생을 찾을 수 없습니다.");
                        }

                        if (users.size() > 1) {
                            throw new IllegalArgumentException(
                                    "동일한 학번의 계정이 여러 개 있습니다.");
                        }

                        User student = users.get(0);

                        // 학생 계정인지 확인
                        if (student.getRole() != Role.STUDENT) {
                            throw new IllegalArgumentException(
                                    "학생 계정이 아닙니다.");
                        }

                        // 학번과 이름 일치 여부 확인
                        if (!student.getName().equals(name)) {
                            throw new IllegalArgumentException(
                                    "학번과 이름이 일치하지 않습니다.");
                        }

                        // 기존 수강 등록 여부 확인
                        if (enrollmentRepository.existsByCourseIdAndUserId(
                                courseId, student.getId())) {
                            throw new IllegalArgumentException(
                                    "이미 등록된 학생입니다.");
                        }

                        // 연락처 저장 또는 수정
                        StudentContact contact = contactRepository
                                .findByUserId(student.getId())
                                .orElse(null);

                        if (contact == null) {
                            contactRepository.save(
                                    new StudentContact(student, phone));
                        } else {
                            contact.updatePhoneNumber(phone);
                        }

                        // 교육에 학생 등록
                        enrollmentRepository.save(
                                new Enrollment(course, student));

                        successCount++;

                    } catch (IllegalArgumentException e) {

                        // 실패한 학생의 행 번호와 사유 기록
                        failures.add(
                                new StudentCsvResult.FailedStudent(
                                        row,
                                        studentNo,
                                        e.getMessage()));
                    }
                }
            }

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "CSV 파일을 읽는 중 오류가 발생했습니다: "
                            + e.getMessage(), e);
        }

        return new StudentCsvResult(
                successCount,
                failures.size(),
                failures);
    }
}
