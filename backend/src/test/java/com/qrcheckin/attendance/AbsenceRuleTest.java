package com.qrcheckin.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 학교 F 기준: 수업일수의 2/3 이상 출석해야 함 = 결석(인정 결석)이 1/3 을 넘으면 F */
class AbsenceRuleTest {

    /** application.yml 의 실제 값 */
    static final double RATIO = 0.3333333333;

    @Test
    void 허용_결석은_전체의_3분의1_내림() {
        assertThat(AbsenceRule.of(15, 0, RATIO).allowedAbsences()).isEqualTo(5); // 15회면 10회 출석 → 결석 5회까지
        assertThat(AbsenceRule.of(16, 0, RATIO).allowedAbsences()).isEqualTo(5); // 16회: 결석 6회면 출석 10회 < 10.67
        assertThat(AbsenceRule.of(9, 0, RATIO).allowedAbsences()).isEqualTo(3);
        assertThat(AbsenceRule.of(8, 0, RATIO).allowedAbsences()).isEqualTo(2);
        assertThat(AbsenceRule.of(3, 0, RATIO).allowedAbsences()).isEqualTo(1);
    }

    @Test
    void 경고_단계() {
        assertThat(AbsenceRule.of(15, 3, RATIO).level()).isEqualTo(AbsenceRule.Level.SAFE);    // 남은 2
        assertThat(AbsenceRule.of(15, 4, RATIO).level()).isEqualTo(AbsenceRule.Level.WARNING); // 남은 1
        assertThat(AbsenceRule.of(15, 5, RATIO).level()).isEqualTo(AbsenceRule.Level.DANGER);  // 남은 0
        assertThat(AbsenceRule.of(15, 6, RATIO).level()).isEqualTo(AbsenceRule.Level.FAIL);    // 출석 9 < 10
    }

    @Test
    void 비율을_분수로_읽는다() {
        assertThat(AbsenceRule.ratioLabel(RATIO)).isEqualTo("1/3");
        assertThat(AbsenceRule.ratioLabel(1 - RATIO)).isEqualTo("2/3");
        assertThat(AbsenceRule.ratioLabel(0.25)).isEqualTo("1/4");
        assertThat(AbsenceRule.ratioLabel(0.37)).isEqualTo("37%");
        assertThat(AbsenceRule.ruleText(RATIO)).isEqualTo("수업의 2/3 이상 출석해야 해요(결석이 1/3을 넘으면 F 기준)");
        assertThat(AbsenceRule.ruleText(0.25)).isEqualTo("수업의 3/4 이상 출석해야 해요(결석이 1/4을 넘으면 F 기준)");
    }
}
