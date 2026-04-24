package com.gakkaweo.backend.support;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BulkDataFixture {

  public record PopulatedIds(
      List<Long> memberIds,
      List<Long> sentenceIds,
      List<Long> sessionIds,
      long adminId,
      String topAuditAction) {}

  private static final int BATCH = 1000;
  private static final List<String> AUDIT_ACTIONS =
      List.of(
          "BAN_MEMBER",
          "UNBAN_MEMBER",
          "SENTENCE_UPLOAD",
          "SENTENCE_DELETE",
          "ANNOUNCEMENT_CREATE");

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public BulkDataFixture(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public PopulatedIds populateLargeDataset(
      int memberCount,
      int bannedCount,
      int sentenceCount,
      int sessionCount,
      int guessesPerSession,
      int auditLogCount,
      int sentenceUploadCount) {

    Instant base = clock.instant().minus(365, ChronoUnit.DAYS);
    List<Long> memberIds = insertMembers(memberCount, bannedCount, base);
    long adminId = memberIds.get(0);
    promoteToAdmin(adminId);
    insertSocialAccounts(memberIds, base);
    insertRefreshTokens(memberIds, base);

    List<Long> sentenceIds = insertDailySentences(sentenceCount, base);
    List<Long> sessionIds = insertGameSessions(memberIds, sentenceIds, sessionCount, base);
    insertGuessHistory(sessionIds, guessesPerSession, base);

    insertAuditLogs(adminId, auditLogCount, base);
    insertSentenceUploads(adminId, sentenceUploadCount, base);

    return new PopulatedIds(memberIds, sentenceIds, sessionIds, adminId, AUDIT_ACTIONS.get(0));
  }

  private List<Long> insertMembers(int count, int bannedCount, Instant base) {
    String sql =
        "INSERT INTO members (public_id, nickname, role, banned, banned_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    for (int i = 0; i < count; i++) {
      boolean banned = i < bannedCount;
      Timestamp created = Timestamp.from(base.plusSeconds(i * 60L));
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            UUID.randomUUID(),
            "user_" + i + "_" + UUID.randomUUID().toString().substring(0, 6),
            "USER",
            banned,
            banned ? created : null,
            created,
            created
          });
    }
    flushRemaining(sql, batch);
    return jdbc.queryForList("SELECT id FROM members ORDER BY id", Long.class);
  }

  private void promoteToAdmin(long memberId) {
    jdbc.update("UPDATE members SET role = 'ADMIN' WHERE id = ?", memberId);
  }

  private void insertSocialAccounts(List<Long> memberIds, Instant base) {
    String sql =
        "INSERT INTO social_accounts (member_id, provider, provider_id, email, connected_at)"
            + " VALUES (?, ?::social_provider, ?, ?, ?)";
    String[] providers = {"KAKAO", "GOOGLE", "NAVER"};
    List<Object[]> batch = new ArrayList<>(BATCH);
    for (int i = 0; i < memberIds.size(); i++) {
      long memberId = memberIds.get(i);
      String provider = providers[i % providers.length];
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            memberId,
            provider,
            provider + "_pid_" + memberId,
            "user" + memberId + "@test.local",
            Timestamp.from(base.plusSeconds(i * 60L))
          });
    }
    flushRemaining(sql, batch);
  }

  private void insertRefreshTokens(List<Long> memberIds, Instant base) {
    String sql =
        "INSERT INTO refresh_tokens "
            + "(member_id, token_hash, family_id, expires_at, created_at, revoked)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    Instant expires = base.plus(30, ChronoUnit.DAYS);
    for (int i = 0; i < memberIds.size(); i++) {
      long memberId = memberIds.get(i);
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            memberId,
            "hash_" + memberId + "_" + UUID.randomUUID(),
            UUID.randomUUID(),
            Timestamp.from(expires),
            Timestamp.from(base.plusSeconds(i * 60L)),
            false
          });
    }
    flushRemaining(sql, batch);
  }

  private List<Long> insertDailySentences(int count, Instant base) {
    String sql =
        "INSERT INTO daily_sentences "
            + "(public_id, sentence, used_at, scheduled_at, status, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    LocalDate today = LocalDate.now(clock);
    for (int i = 0; i < count; i++) {
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            UUID.randomUUID(),
            "목데이터 문장 " + i,
            java.sql.Date.valueOf(today.minusDays(count - i)),
            java.sql.Date.valueOf(today.plusDays(i + 1)),
            "ACTIVE",
            Timestamp.from(base.plusSeconds(i * 60L))
          });
    }
    flushRemaining(sql, batch);
    return jdbc.queryForList("SELECT id FROM daily_sentences ORDER BY id", Long.class);
  }

  private List<Long> insertGameSessions(
      List<Long> memberIds, List<Long> sentenceIds, int count, Instant base) {
    String sql =
        "INSERT INTO game_sessions "
            + "(public_id, member_id, sentence_id, status, best_similarity, attempt_count,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    int memberSize = memberIds.size();
    int sentenceSize = sentenceIds.size();
    int generated = 0;
    for (int slot = 0; slot < memberSize * sentenceSize && generated < count; slot++) {
      int memberIdx = slot % memberSize;
      int sentenceIdx = slot / memberSize;
      Timestamp created = Timestamp.from(base.plusSeconds(generated * 30L));
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            UUID.randomUUID(),
            memberIds.get(memberIdx),
            sentenceIds.get(sentenceIdx),
            "IN_PROGRESS",
            0.0,
            0,
            created,
            created
          });
      generated++;
    }
    flushRemaining(sql, batch);
    return jdbc.queryForList("SELECT id FROM game_sessions ORDER BY id", Long.class);
  }

  private void insertGuessHistory(List<Long> sessionIds, int avgPerSession, Instant base) {
    String sql =
        "INSERT INTO guess_history "
            + "(session_id, guess_text, similarity, attempt_number, created_at)"
            + " VALUES (?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    long clock = 0;
    for (long sessionId : sessionIds) {
      for (int attempt = 1; attempt <= avgPerSession; attempt++) {
        addAndMaybeFlush(
            sql,
            batch,
            new Object[] {
              sessionId,
              "guess_" + sessionId + "_" + attempt,
              (attempt * 5.0) % 100,
              attempt,
              Timestamp.from(base.plusSeconds(clock++))
            });
      }
    }
    flushRemaining(sql, batch);
  }

  private void insertAuditLogs(long adminId, int count, Instant base) {
    String sql =
        "INSERT INTO audit_logs "
            + "(admin_id, action, target_type, target_id, detail, ip_address, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    for (int i = 0; i < count; i++) {
      String action = AUDIT_ACTIONS.get(i % AUDIT_ACTIONS.size());
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            adminId,
            action,
            "MEMBER",
            String.valueOf(i),
            "detail_" + i,
            "127.0.0.1",
            Timestamp.from(base.plusSeconds(i * 10L))
          });
    }
    flushRemaining(sql, batch);
  }

  private void insertSentenceUploads(long adminId, int count, Instant base) {
    String sql =
        "INSERT INTO sentence_uploads (admin_id, file_name, record_count, created_at)"
            + " VALUES (?, ?, ?, ?)";
    List<Object[]> batch = new ArrayList<>(BATCH);
    for (int i = 0; i < count; i++) {
      addAndMaybeFlush(
          sql,
          batch,
          new Object[] {
            adminId, "upload_" + i + ".csv", 100, Timestamp.from(base.plusSeconds(i * 3600L))
          });
    }
    flushRemaining(sql, batch);
  }

  private void addAndMaybeFlush(String sql, List<Object[]> batch, Object[] row) {
    batch.add(row);
    if (batch.size() >= BATCH) {
      jdbc.batchUpdate(sql, batch);
      batch.clear();
    }
  }

  private void flushRemaining(String sql, List<Object[]> batch) {
    if (!batch.isEmpty()) {
      jdbc.batchUpdate(sql, batch);
      batch.clear();
    }
  }
}
