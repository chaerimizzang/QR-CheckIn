package com.qrcheckin.course;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * 학기(연도 + 종류). 날짜로 정할 때 규칙:
 * 3~6월 1학기, 7~8월 여름학기, 9~12월 2학기, 1~2월 전년도 겨울학기.
 */
public record Semester(int year, SemesterTerm term) {

    public static final Comparator<Semester> NEWEST_FIRST =
            Comparator.comparingInt(Semester::year).thenComparing(Semester::term).reversed();

    public static Semester of(LocalDate date) {
        int m = date.getMonthValue();
        if (m <= 2) {
            return new Semester(date.getYear() - 1, SemesterTerm.WINTER);
        }
        if (m <= 6) {
            return new Semester(date.getYear(), SemesterTerm.SPRING);
        }
        if (m <= 8) {
            return new Semester(date.getYear(), SemesterTerm.SUMMER);
        }
        return new Semester(date.getYear(), SemesterTerm.FALL);
    }

    /** 예: "2026년 2학기" */
    public String label() {
        return year + "년 " + term.label();
    }

    /** 예: "2026-FALL" (화면의 학기 선택 값) */
    public String key() {
        return year + "-" + term.name();
    }
}
