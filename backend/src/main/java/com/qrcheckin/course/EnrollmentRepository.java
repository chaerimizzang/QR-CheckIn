package com.qrcheckin.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndUserId(Long courseId, Long userId);

    Optional<Enrollment> findByCourseIdAndUserId(Long courseId, Long userId);

    List<Enrollment> findByCourseId(Long courseId);

    List<Enrollment> findByUserId(Long userId);

    long countByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);
}
