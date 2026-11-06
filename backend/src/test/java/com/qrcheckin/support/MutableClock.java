package com.qrcheckin.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트용 시계: 원하는 만큼 시간을 앞으로 돌릴 수 있다 */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    public void advance(Duration d) {
        instant = instant.plus(d);
    }

    public void set(Instant newInstant) {
        instant = newInstant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId z) {
        return new MutableClock(instant, z);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
