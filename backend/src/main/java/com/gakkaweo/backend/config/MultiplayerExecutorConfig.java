package com.gakkaweo.backend.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@RequiredArgsConstructor
public class MultiplayerExecutorConfig {

  private final MultiplayerProperties properties;

  @Bean
  public ScheduledExecutorService multiplayerTimerExecutor() {
    return Executors.newScheduledThreadPool(
        properties.timer().poolSize(),
        r -> {
          Thread t = new Thread(r, "multi-timer");
          t.setDaemon(true);
          return t;
        });
  }

  @Bean
  public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("stomp-heartbeat-");
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }
}
