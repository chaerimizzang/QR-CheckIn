
package com.qrcheckin.course;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentContactRepository
        extends JpaRepository<StudentContact, Long> {

    Optional<StudentContact> findByUserId(Long userId);
}
