package com.qrcheckin.course;

import java.time.LocalDate;

import com.qrcheckin.user.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CourseDtos {

    private CourseDtos() {
    }

    /** B-02 교육 생성·수정 */
    public record CourseRequest(
            @NotBlank @Size(max = 100) String title,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record CourseResponse(Long id, String title, LocalDate startDate, LocalDate endDate,
                                 String ownerName, long studentCount) {
        public static CourseResponse of(Course c, long studentCount) {
            return new CourseResponse(c.getId(), c.getTitle(), c.getStartDate(), c.getEndDate(),
                    c.getOwner().getName(), studentCount);
        }
    }

    /** B-04 수강생 추가 — 가입한 계정의 이메일 */
    public record EnrollmentRequest(@NotBlank @Email String email) {
    }

    public record StudentResponse(Long userId, String email, String name, String studentNo) {
        public static StudentResponse from(User u) {
            return new StudentResponse(u.getId(), u.getEmail(), u.getName(), u.getStudentNo());
        }
    }
}
