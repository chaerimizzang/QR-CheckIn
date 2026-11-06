package com.qrcheckin.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.qrcheckin.checkin.GeoUtils.LocationVerdict;

class GeoUtilsTest {

    @Test
    void 같은_점의_거리는_0() {
        assertThat(GeoUtils.distanceMeters(37.5665, 126.9780, 37.5665, 126.9780)).isEqualTo(0.0);
    }

    @Test
    void 위도_0_001도는_약_111m() {
        double d = GeoUtils.distanceMeters(37.0, 127.0, 37.001, 127.0);
        assertThat(d).isCloseTo(111.19, within(0.5));
    }

    @Test
    void 서울시청에서_광화문까지_약_1_1km() {
        double d = GeoUtils.distanceMeters(37.5663, 126.9779, 37.5759, 126.9768);
        assertThat(d).isBetween(1000.0, 1200.0);
    }

    @Test
    void 반경_50m_거리_49m는_통과_51m는_OUT_OF_RANGE() {
        assertThat(GeoUtils.judge(49, 20, 50, 100)).isEqualTo(LocationVerdict.OK);
        assertThat(GeoUtils.judge(50, 20, 50, 100)).isEqualTo(LocationVerdict.OK);
        assertThat(GeoUtils.judge(51, 20, 50, 100)).isEqualTo(LocationVerdict.OUT_OF_RANGE);
    }

    @Test
    void accuracy_150m는_LOW_ACCURACY() {
        assertThat(GeoUtils.judge(10, 150, 50, 100)).isEqualTo(LocationVerdict.LOW_ACCURACY);
        assertThat(GeoUtils.judge(500, 150, 50, 100)).isEqualTo(LocationVerdict.LOW_ACCURACY);
    }
}
