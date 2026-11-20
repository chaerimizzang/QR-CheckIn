
package com.qrcheckin.course;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

@Service
public class StudentCsvService {

    static final List<String> HEADER = List.of("학번", "이름", "이메일");

    private final CourseService courseService;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public StudentCsvService(
            CourseService courseService,
            UserRepository userRepository,
            EnrollmentRepository enrollmentRepository) {

        this.courseService = courseService;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * CSV(학번,이름,이메일)로 수강생을 한꺼번에 등록한다.
     * 회원가입한 학생만 등록되고, 이메일로 찾은 계정의 학번·이름이 CSV 와 같아야 한다.
     * 실패한 줄은 건너뛰고 줄 번호와 이유를 돌려준다.
     */
    @Transactional
    public StudentCsvResult upload(
            Long courseId, MultipartFile file, AuthUser admin) {

        // 관리자 권한 및 교육 소유 여부 확인
        Course course = courseService.getOwnedCourse(courseId, admin);

        List<StudentCsvResult.FailedStudent> failures = new ArrayList<>();
        Set<String> processedEmails = new HashSet<>();
        int successCount = 0;

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "CSV 파일이 비어 있어요");
        }

        try (
            // 엑셀 기본 'CSV(쉼표로 분리)'는 한글을 CP949 로 저장한다 → UTF-8 이 아니면 CP949 로 읽는다
            StringReader reader = new StringReader(decode(file.getBytes()))
        ) {

            try (CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .get()
                    .parse(reader)) {

                // CSV 필수 열 확인
                if (!parser.getHeaderMap().keySet().containsAll(HEADER)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "CSV 첫 줄에는 학번,이름,이메일이 필요해요");
                }

                for (CSVRecord record : parser) {

                    int row = (int) record.getRecordNumber() + 1;
                    String studentNo = "";

                    try {
                        studentNo = record.get("학번").trim();
                        String name = record.get("이름").trim();
                        String email = record.get("이메일").trim().toLowerCase(Locale.ROOT);

                        // 필수 항목 검사
                        if (studentNo.isEmpty() || name.isEmpty() || email.isEmpty()) {
                            throw new IllegalArgumentException("비어 있는 칸이 있어요");
                        }

                        // CSV 파일 내부 중복 검사
                        if (!processedEmails.add(email)) {
                            throw new IllegalArgumentException("CSV 안에 같은 이메일이 또 있어요");
                        }

                        // 이메일로 가입한 계정 찾기
                        User student = userRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "회원가입하지 않은 이메일이에요"));

                        // 학생 계정인지 확인
                        if (student.getRole() != Role.STUDENT) {
                            throw new IllegalArgumentException("학생 계정이 아니에요");
                        }

                        // 학번·이름이 가입 정보와 같은지 확인
                        if (!studentNo.equals(student.getStudentNo())) {
                            throw new IllegalArgumentException("가입 정보의 학번과 달라요");
                        }
                        if (!name.equals(student.getName())) {
                            throw new IllegalArgumentException("가입 정보의 이름과 달라요");
                        }

                        // 기존 수강 등록 여부 확인
                        if (enrollmentRepository.existsByCourseIdAndUserId(
                                courseId, student.getId())) {
                            throw new IllegalArgumentException("이미 등록된 학생이에요");
                        }

                        // 교육에 학생 등록
                        enrollmentRepository.save(new Enrollment(course, student));
                        successCount++;

                    } catch (IllegalArgumentException e) {

                        // 실패한 학생의 행 번호와 사유 기록
                        failures.add(new StudentCsvResult.FailedStudent(
                                row, studentNo, e.getMessage()));
                    }
                }
            }

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "CSV 파일을 읽지 못했어요. 쉼표(,)로 구분된 CSV 파일인지 확인해 주세요");
        }

        return new StudentCsvResult(successCount, failures.size(), failures);
    }

    /** UTF-8(BOM 포함)로 읽고, UTF-8 이 아니면 CP949(엑셀 한글 기본)로 읽는다 */
    static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("MS949"));
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
