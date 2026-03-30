package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

  Optional<GameSession> findByMemberAndSentence(Member member, DailySentence sentence);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE GameSession g SET g.status ="
          + " com.gakkaweo.backend.domain.game.entity.GameSessionStatus.EXPIRED"
          + " WHERE g.sentence = :sentence"
          + " AND g.status ="
          + " com.gakkaweo.backend.domain.game.entity.GameSessionStatus.IN_PROGRESS")
  int expireInProgressSessions(@Param("sentence") DailySentence sentence);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM GameSession g WHERE g.sentence = :sentence")
  int deleteBySentence(@Param("sentence") DailySentence sentence);

  @Query("SELECT g FROM GameSession g JOIN FETCH g.member WHERE g.sentence = :sentence")
  List<GameSession> findAllBySentenceWithMember(@Param("sentence") DailySentence sentence);

  @Modifying(clearAutomatically = true)
  @Query("UPDATE GameSession g SET g.member = null WHERE g.member = :member")
  int anonymizeByMember(@Param("member") Member member);

  @Query(
      "SELECT COUNT(g) FROM GameSession g WHERE g.sentence = :sentence"
          + " AND g.status = com.gakkaweo.backend.domain.game.entity.GameSessionStatus.CLEARED")
  long countClearedBySentence(@Param("sentence") DailySentence sentence);

  @Query("SELECT COUNT(g) FROM GameSession g WHERE g.sentence = :sentence")
  long countBySentence(@Param("sentence") DailySentence sentence);

  @Query(
      "SELECT COALESCE(AVG(g.bestSimilarity), 0) FROM GameSession g WHERE g.sentence = :sentence")
  BigDecimal avgSimilarityBySentence(@Param("sentence") DailySentence sentence);

  @Query("SELECT COALESCE(AVG(g.attemptCount), 0) FROM GameSession g WHERE g.sentence = :sentence")
  double avgAttemptCountBySentence(@Param("sentence") DailySentence sentence);

  @Query("SELECT COUNT(g) FROM GameSession g WHERE g.member = :member")
  long countByMember(@Param("member") Member member);

  @Query(
      "SELECT COUNT(g) FROM GameSession g WHERE g.member = :member"
          + " AND g.status = com.gakkaweo.backend.domain.game.entity.GameSessionStatus.CLEARED")
  long countClearedByMember(@Param("member") Member member);

  @Query("SELECT COALESCE(AVG(g.attemptCount), 0) FROM GameSession g WHERE g.member = :member")
  double avgAttemptCountByMember(@Param("member") Member member);

  @Query(
      "SELECT MIN(g.finalRank) FROM GameSession g WHERE g.member = :member"
          + " AND g.finalRank IS NOT NULL")
  Integer bestRankByMember(@Param("member") Member member);

  @Query(
      "SELECT g FROM GameSession g JOIN FETCH g.sentence WHERE g.member = :member"
          + " ORDER BY g.createdAt DESC")
  List<GameSession> findByMemberWithSentence(@Param("member") Member member);

  @Query(
      "SELECT COUNT(g) FROM GameSession g WHERE g.sentence = :sentence"
          + " AND g.status = com.gakkaweo.backend.domain.game.entity.GameSessionStatus.CLEARED")
  long countClearedBySentenceForDate(@Param("sentence") DailySentence sentence);

  @Query(
      "SELECT COUNT(DISTINCT g.member) FROM GameSession g"
          + " WHERE g.sentence = :sentence AND g.member IS NOT NULL")
  long countDistinctMembersBySentence(@Param("sentence") DailySentence sentence);
}
