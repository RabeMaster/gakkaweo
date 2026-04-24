package com.gakkaweo.backend.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.support.BulkDataFixture;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("performance")
class IndexPerformanceTest extends IntegrationTestBase {

  private static final Logger log = LoggerFactory.getLogger(IndexPerformanceTest.class);

  @Autowired private BulkDataFixture bulkDataFixture;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("V15 대상 7개 쿼리가 모두 Index Scan으로 전환되는지 측정")
  void allTargetQueriesShouldUseIndexScan() {
    BulkDataFixture.PopulatedIds data =
        bulkDataFixture.populateLargeDataset(5_000, 1_500, 500, 20_000, 10, 10_000, 500);

    long targetMemberId = data.memberIds().get(100);
    long targetSessionId = data.sessionIds().get(data.sessionIds().size() / 2);

    assertIndexUsed(
        "1. sentence_uploads(admin_id)",
        "idx_sentence_uploads_admin",
        "SELECT * FROM sentence_uploads WHERE admin_id = ? ORDER BY created_at DESC LIMIT 20",
        data.adminId());

    assertIndexUsed(
        "2. social_accounts(member_id)",
        "idx_social_accounts_member",
        "SELECT * FROM social_accounts WHERE member_id = ?",
        targetMemberId);

    assertIndexUsed(
        "3. refresh_tokens(member_id)",
        "idx_refresh_tokens_member",
        "SELECT * FROM refresh_tokens WHERE member_id = ?",
        targetMemberId);

    assertIndexUsed(
        "4. game_sessions(member_id, created_at DESC)",
        "idx_game_sessions_member_created",
        "SELECT * FROM game_sessions WHERE member_id = ? ORDER BY created_at DESC LIMIT 20",
        targetMemberId);

    assertIndexUsed(
        "5. audit_logs(action, created_at DESC)",
        "idx_audit_logs_action_created",
        "SELECT * FROM audit_logs WHERE action = ? ORDER BY created_at DESC LIMIT 50",
        data.topAuditAction());

    assertIndexUsed(
        "6. guess_history(session_id, attempt_number)",
        "idx_guess_history_session_attempt",
        "SELECT * FROM guess_history WHERE session_id = ? ORDER BY attempt_number",
        targetSessionId);

    assertIndexUsed(
        "7. members(banned, created_at DESC)",
        "idx_members_banned_created",
        "SELECT * FROM members WHERE banned = false ORDER BY created_at DESC LIMIT 20");
  }

  private void assertIndexUsed(String title, String expectedIndex, String sql, Object... params) {
    List<String> plan =
        jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class, params);
    String joined = String.join("\n", plan);
    log.info("\n===== {} =====\n{}\n", title, joined);
    assertThat(joined)
        .as("[%s] V15 인덱스 '%s'가 플랜에 포함되어야 함", title, expectedIndex)
        .contains(expectedIndex);
  }
}
