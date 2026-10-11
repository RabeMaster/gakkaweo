package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

@DisplayName("V18 인증 세션 마이그레이션 테스트")
class V18MigrationTest {

  @Test
  @DisplayName("V18은 이전 토큰을 폐기하고 숫자 세션 ID와 갱신 토큰 연결을 적용한다")
  void V17_이전토큰_폐기_숫자세션_연결() throws Exception {
    try (var postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_migration_test")
            .withUsername("test")
            .withPassword("test")) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .target("17")
          .load()
          .migrate();

      UUID active = UUID.randomUUID();
      UUID revoked = UUID.randomUUID();

      try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
          var statement = connection.createStatement()) {
        statement.execute("INSERT INTO members (id, nickname) VALUES (1, 'migration-fixture')");
        try (var insert =
            connection.prepareStatement(
                "INSERT INTO refresh_tokens (member_id, token_hash, family_id, expires_at, revoked) VALUES (1, ?, ?, NOW() + INTERVAL '1 day', ?)")) {
          for (int i = 0; i < 3; i++) {
            insert.setString(1, "migration-token-" + i);
            insert.setObject(2, i < 2 ? active : revoked);
            insert.setBoolean(3, i != 1);
            insert.executeUpdate();
          }
        }

        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), "test", "test")
            .locations("classpath:db/migration")
            .target("18")
            .load()
            .migrate();

        try (var result = statement.executeQuery("SELECT entity_version FROM members WHERE id=1")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        try (var result = statement.executeQuery("SELECT count(*) FROM auth_sessions")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        try (var result =
            statement.executeQuery("SELECT count(*) FROM refresh_tokens WHERE revoked")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isEqualTo(3);
        }

        try (var result =
            statement.executeQuery(
                "SELECT count(*) FROM refresh_tokens WHERE session_id IS NULL")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isEqualTo(3);
        }

        UUID sessionPublicId = UUID.randomUUID();
        long sessionId;
        try (var insert =
            connection.prepareStatement(
                "INSERT INTO auth_sessions (public_id, member_id, created_at, expires_at)"
                    + " VALUES (?, 1, NOW(), NOW() + INTERVAL '1 day') RETURNING id")) {
          insert.setObject(1, sessionPublicId);
          try (var result = insert.executeQuery()) {
            assertThat(result.next()).isTrue();
            sessionId = result.getLong(1);

            assertThat(sessionId).isPositive();
          }
        }

        statement.execute(
            "UPDATE refresh_tokens SET session_id=" + sessionId + " WHERE member_id=1");
        statement.execute("DELETE FROM auth_sessions WHERE id=" + sessionId);

        try (var result = statement.executeQuery("SELECT count(*) FROM refresh_tokens")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        statement.execute("DELETE FROM refresh_tokens WHERE member_id=1");
        statement.execute("DELETE FROM members WHERE id=1");

        try (var result = statement.executeQuery("SELECT count(*) FROM auth_sessions")) {
          result.next();

          assertThat(result.getLong(1)).isZero();
        }
      }
    }
  }
}
