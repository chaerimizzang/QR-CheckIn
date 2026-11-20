package com.qrcheckin.schedule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);
}
