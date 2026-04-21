package com.gakkaweo.backend.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfig {

  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("gakkaweo_test")
        .withUsername("test")
        .withPassword("test")
        .withUrlParam("stringtype", "unspecified")
        .withReuse(true);
  }

  @Bean
  @ServiceConnection(name = "redis")
  public RedisContainer redisContainer() {
    return new RedisContainer(DockerImageName.parse("redis:7-alpine")).withReuse(true);
  }
}
