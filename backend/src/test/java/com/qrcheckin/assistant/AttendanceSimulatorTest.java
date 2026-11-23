package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.qrcheckin.attendance.AbsenceRule.Level;

/** 가정 계산: 출석률·결석 경고와 같은 규칙(지각 3 = 결석 1, 1/4 초과 F)인지, 경계값에서 맞는지 */
class AttendanceSimulatorTest {

    static final double RATIO = 0.25;

    @Test
    void 지각_환산_직전과_직후() {
        // 15회 → 허용 3. 결석 2·지각 2 → 인정 2(주의), 지각 1 더 → 인정 3(위험)
        var r = AttendanceSimulator.simulate(15, 5, 2, 2, 1, 0, RATIO);
        assertThat(r.current().convertedAbsences()).isEqualTo(2);
        assertThat(r.current().allowedAbsences()).isEqualTo(3);
        assertThat(r.current().remainingAbsences()).isEqualTo(1);
        assertThat(r.current().level()).isEqualTo(Level.WARNING);
        assertThat(r.simulated().lateCount()).isEqualTo(3);
        assertThat(r.simulated().convertedAbsences()).isEqualTo(3);
        assertThat(r.simulated().remainingAbsences()).isZero();
        assertThat(r.simulated().level()).isEqualTo(Level.DANGER);
        assertThat(r.simulated().message()).isEqualTo("1번만 더 결석해도 F 기준을 넘어요");
        assertThat(r.levelChanged()).isTrue();
    }

    @Test
    void 지각이_3회가_안_되면_인정_결석은_그대로() {
        var r = AttendanceSimulator.simulate(15, 5, 1, 0, 2, 0, RATIO);
        assertThat(r.simulated().convertedAbsences()).isEqualTo(1);
        assertThat(r.levelChanged()).isFalse();
    }

    @Test
    void 허용_결석을_넘으면_F_기준() {
        // 허용 3, 인정 3(위험) → 결석 1 더 → 4(남은 -1, FAIL)
        var r = AttendanceSimulator.simulate(15, 3, 3, 0, 0, 1, RATIO);
        assertThat(r.current().level()).isEqualTo(Level.DANGER);
        assertThat(r.simulated().remainingAbsences()).isEqualTo(-1);
        assertThat(r.simulated().level()).isEqualTo(Level.FAIL);
        assertThat(r.simulated().message()).isEqualTo("결석이 수업의 1/4을 넘었어요(F 기준)");
    }

    @Test
    void 결석이_없으면_경고하지_않는다() {
        var r = AttendanceSimulator.simulate(15, 5, 0, 0, 2, 0, RATIO);
        assertThat(r.simulated().convertedAbsences()).isZero();
        assertThat(r.simulated().level()).isEqualTo(Level.SAFE);
    }

    @Test
    void 회차가_0개면_NONE() {
        var s = AttendanceSimulator.snapshot(0, 0, 0, RATIO);
        assertThat(s.level()).isEqualTo(Level.NONE);
        assertThat(s.remainingAbsences()).isNull();
    }

    @Test
    void 남은_회차보다_많이_가정할_수_없다() {
        assertThatThrownBy(() -> AttendanceSimulator.simulate(15, 2, 0, 0, 2, 1, RATIO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("남은 수업이 2회라 그보다 많이 가정할 수 없어요");
        assertThatThrownBy(() -> AttendanceSimulator.simulate(15, 0, 0, 0, 1, 0, RATIO))
                .hasMessage("남은 수업이 없어서 더 가정할 수 없어요");
    }

    @Test
    void 잘못된_횟수() {
        assertThatThrownBy(() -> AttendanceSimulator.simulate(15, 5, 0, 0, 0, 0, RATIO))
                .hasMessage("몇 번 더 지각·결석하는지 알려 주세요");
        assertThatThrownBy(() -> AttendanceSimulator.simulate(15, 5, 0, 0, -1, 2, RATIO))
                .hasMessage("지각·결석 횟수는 0 이상이어야 해요");
        assertThatThrownBy(() -> AttendanceSimulator.simulate(40, 30, 0, 0, 21, 0, RATIO))
                .hasMessage("한 번에 20회까지만 가정할 수 있어요");
    }
}
