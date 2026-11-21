package com.qrcheckin.common;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * 교육·회차·수강생을 지울 때, 그것을 가리키는 기록(출결 수정 이력, 출석 인정 신청·증빙)을 먼저 지운다.
 * 이 기록들이 남아 있으면 외래 키 때문에 삭제가 실패한다(500).
 * 출결(attendance) 자체는 각 서비스가 이어서 지운다. 호출하는 쪽의 트랜잭션 안에서 실행된다.
 */
@Component
public class DependentDataCleaner {

    private static final String SESSIONS_OF_COURSE = "select s.id from ClassSession s where s.course.id = :course";

    private final EntityManager em;

    public DependentDataCleaner(EntityManager em) {
        this.em = em;
    }

    /** 교육 전체 */
    public void forCourse(Long courseId) {
        run("delete from RequestEvidence e where e.requestId in (select r.id from AttendanceRequest r"
                + " where r.session.id in (" + SESSIONS_OF_COURSE + "))", "course", courseId, null, null);
        run("delete from AttendanceRequest r where r.session.id in (" + SESSIONS_OF_COURSE + ")",
                "course", courseId, null, null);
        run("delete from AttendanceHistory h where h.attendance.id in (select a.id from Attendance a"
                + " where a.session.id in (" + SESSIONS_OF_COURSE + "))", "course", courseId, null, null);
    }

    /** 회차 하나(출결 기록까지 지운다 — 예정 회차에도 미리 승인된 공결 기록이 있을 수 있다) */
    public void forSession(Long sessionId) {
        run("delete from RequestEvidence e where e.requestId in (select r.id from AttendanceRequest r"
                + " where r.session.id = :session)", "session", sessionId, null, null);
        run("delete from AttendanceRequest r where r.session.id = :session", "session", sessionId, null, null);
        run("delete from AttendanceHistory h where h.attendance.id in (select a.id from Attendance a"
                + " where a.session.id = :session)", "session", sessionId, null, null);
        run("delete from Attendance a where a.session.id = :session", "session", sessionId, null, null);
    }

    /** 교육에서 수강생 한 명을 뺄 때 */
    public void forStudentInCourse(Long courseId, Long userId) {
        run("delete from RequestEvidence e where e.requestId in (select r.id from AttendanceRequest r"
                + " where r.student.id = :user and r.session.id in (" + SESSIONS_OF_COURSE + "))",
                "course", courseId, "user", userId);
        run("delete from AttendanceRequest r where r.student.id = :user and r.session.id in ("
                + SESSIONS_OF_COURSE + ")", "course", courseId, "user", userId);
        run("delete from AttendanceHistory h where h.attendance.id in (select a.id from Attendance a"
                + " where a.user.id = :user and a.session.id in (" + SESSIONS_OF_COURSE + "))",
                "course", courseId, "user", userId);
    }

    private void run(String jpql, String p1, Object v1, String p2, Object v2) {
        var q = em.createQuery(jpql).setParameter(p1, v1);
        if (p2 != null) {
            q.setParameter(p2, v2);
        }
        q.executeUpdate();
    }
}
