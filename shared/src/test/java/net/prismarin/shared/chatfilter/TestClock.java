package net.prismarin.shared.chatfilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

final class TestClock extends Clock {

    private final AtomicLong now;

    TestClock(long start) {
        this.now = new AtomicLong(start);
    }

    void advance(long millis) {
        now.addAndGet(millis);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(now.get());
    }

    @Override
    public long millis() {
        return now.get();
    }
}
