package com.gakkaweo.backend.infra.notification.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class DiscordWebhookConfig {

  @Bean
  RestClient discordWebhookRestClient(DiscordWebhookProperties properties) {
    int timeoutMillis = Math.toIntExact(properties.timeout().toMillis());

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeoutMillis);
    factory.setReadTimeout(timeoutMillis);

    return RestClient.builder().requestFactory(factory).build();
  }

  @Bean
  Executor discordWebhookExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("discord-webhook-");
    executor.initialize();
    return executor;
  }
}
