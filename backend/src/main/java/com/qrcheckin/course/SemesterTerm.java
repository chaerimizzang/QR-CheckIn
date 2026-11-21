package com.qrcheckin.course;

/** 학기 종류(한 해 안의 순서대로) */
public enum SemesterTerm {
    SPRING("1학기"),
    SUMMER("여름학기"),
    FALL("2학기"),
    WINTER("겨울학기");

    private final String label;

    SemesterTerm(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
