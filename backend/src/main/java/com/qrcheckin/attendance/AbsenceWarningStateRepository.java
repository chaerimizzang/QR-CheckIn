package com.qrcheckin.attendance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AbsenceWarningStateRepository extends JpaRepository<AbsenceWarningState, Long> {

    List<AbsenceWarningState> findByCourseId(Long courseId);

    @Transactional
    void deleteByCourseId(Long courseId);
}
