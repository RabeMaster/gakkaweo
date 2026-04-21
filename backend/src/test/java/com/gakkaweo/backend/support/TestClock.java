package com.gakkaweo.backend.support;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class TestClock extends Clock {

  private final ZoneId zone;
  private Instant instant;

  public TestClock(Instant initial) {
    this(initial, KST);
  }

  public TestClock(Instant initial, ZoneId zone) {
    this.instant = initial;
    this.zone = zone;
  }

  public static TestClock systemDefault() {
    return new TestClock(Clock.system(KST).instant());
  }

  public void setInstant(Instant instant) {
    this.instant = instant;
  }

  public void advanceBy(Duration duration) {
    this.instant = this.instant.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new TestClock(this.instant, zone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
