
package com.qrcheckin.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrcheckin.user.User;

public interface StudentCsvUserRepository extends JpaRepository<User, Long> {

    List<User> findByStudentNo(String studentNo);
}
