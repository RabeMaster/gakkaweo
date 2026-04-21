package com.gakkaweo.backend.support;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

  private final JdbcTemplate jdbcTemplate;
  private final RedisConnectionFactory redisConnectionFactory;

  public DatabaseCleaner(JdbcTemplate jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
    this.jdbcTemplate = jdbcTemplate;
    this.redisConnectionFactory = redisConnectionFactory;
  }

  public void clean() {
    truncateTables();
    flushRedis();
  }

  private void truncateTables() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "AND tablename <> 'flyway_schema_history'",
            String.class);
    if (tables.isEmpty()) {
      return;
    }
    String joined = tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "));
    jdbcTemplate.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
  }

  private void flushRedis() {
    try (RedisConnection connection = redisConnectionFactory.getConnection()) {
      connection.serverCommands().flushAll();
    }
  }
}
