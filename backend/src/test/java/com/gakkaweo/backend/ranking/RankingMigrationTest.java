package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("V18에서 V19 최고 기록 이관 통합 테스트")
class RankingMigrationTest extends IntegrationTestBase {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("최초 최고 기록·클리어 후 개선·이력 누락·999 초과와 확정 순위 보존")
  void 최고_기록_이관() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getCatalog()).isEqualTo("gakkaweo_test");
    }
    String schema = "ranking_migration_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .target("18")
          .load()
          .migrate();
      jdbcTemplate.execute(
          "INSERT INTO "
              + schema
              + ".members(id,nickname) VALUES (1,'최초'),(2,'개선'),(3,'누락'),(4,'초과')");
      jdbcTemplate.execute(
          "INSERT INTO "
              + schema
              + ".daily_sentences(id,sentence,used_at,total_players) VALUES (1,'이관 문장','2026-10-01',12)");
      jdbcTemplate.execute(
          "INSERT INTO "
              + schema
              + ".game_sessions(id,member_id,sentence_id,best_similarity,attempt_count,updated_at,final_rank) VALUES"
              + " (1,1,1,90,5,'2026-10-01T12:00:00Z',7),"
              + " (2,2,1,100,3,'2026-10-01T12:00:00Z',1),"
              + " (3,3,1,70,6,'2026-10-01T12:00:00Z',9),"
              + " (4,4,1,80,1200,'2026-10-01T12:00:00Z',8)");
      jdbcTemplate.execute(
          "INSERT INTO "
              + schema
              + ".guess_history(session_id,guess_text,similarity,attempt_number,created_at) VALUES"
              + " (1,'최초 최고',90,2,'2026-10-01T10:02:00Z'),"
              + " (1,'반복 최고',90,4,'2026-10-01T10:04:00Z'),"
              + " (2,'클리어 뒤 개선',100,8,'2026-10-01T10:08:00Z'),"
              + " (4,'상한 초과',80,1100,'2026-10-01T11:00:00Z')");

      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();

      assertThat(
              jdbcTemplate.queryForList(
                  "SELECT ranking_attempt_count FROM " + schema + ".game_sessions ORDER BY id",
                  Integer.class))
          .containsExactly(2, 3, 6, 1100);
      assertThat(
              jdbcTemplate
                  .queryForObject(
                      "SELECT ranking_achieved_at FROM " + schema + ".game_sessions WHERE id=1",
                      java.sql.Timestamp.class)
                  .toInstant())
          .isEqualTo(Instant.parse("2026-10-01T10:02:00Z"));
      assertThat(
              jdbcTemplate
                  .queryForObject(
                      "SELECT ranking_achieved_at FROM " + schema + ".game_sessions WHERE id=3",
                      java.sql.Timestamp.class)
                  .toInstant())
          .isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
      assertThat(
              jdbcTemplate.queryForList(
                  "SELECT final_rank FROM " + schema + ".game_sessions ORDER BY id", Integer.class))
          .containsExactly(7, 1, 9, 8);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT total_players FROM " + schema + ".daily_sentences WHERE id=1",
                  Integer.class))
          .isEqualTo(12);
    } finally {
      jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }
}
