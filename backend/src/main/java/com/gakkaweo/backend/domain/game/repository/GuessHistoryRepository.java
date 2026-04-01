package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuessHistoryRepository extends JpaRepository<GuessHistory, Long> {

  List<GuessHistory> findBySessionOrderByAttemptNumberAsc(GameSession session);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM GuessHistory h WHERE h.session.sentence = :sentence")
  int deleteBySentence(@Param("sentence") DailySentence sentence);

  long countBySession(GameSession session);

  @Query(
      value =
          "SELECT h FROM GuessHistory h JOIN FETCH h.session s LEFT JOIN FETCH s.member"
              + " WHERE s.sentence = :sentence ORDER BY h.createdAt DESC",
      countQuery = "SELECT COUNT(h) FROM GuessHistory h WHERE h.session.sentence = :sentence")
  Page<GuessHistory> findBySentenceOrderByCreatedAtDesc(
      @Param("sentence") DailySentence sentence, Pageable pageable);

  @Query(
      "SELECT h FROM GuessHistory h JOIN FETCH h.session s JOIN FETCH s.member"
          + " WHERE s.sentence = :sentence AND s.member IS NOT NULL"
          + " AND s.member.publicId = :memberPublicId ORDER BY h.attemptNumber ASC")
  List<GuessHistory> findBySentenceAndMember(
      @Param("sentence") DailySentence sentence, @Param("memberPublicId") UUID memberPublicId);

  @Query(
      value =
          "SELECT sub.guess_text AS guessText, sub.similarity"
              + " FROM (SELECT DISTINCT ON (h.guess_text) h.guess_text, h.similarity"
              + " FROM guess_history h JOIN game_sessions gs ON h.session_id = gs.id"
              + " WHERE gs.sentence_id = :sentenceId"
              + " AND (gs.member_id IS NULL OR gs.member_id != :memberId)"
              + " AND h.similarity < :maxSimilarity"
              + " ORDER BY h.guess_text, h.similarity DESC) sub"
              + " ORDER BY sub.similarity DESC LIMIT :maxCount",
      nativeQuery = true)
  List<HintProjection> findHints(
      @Param("sentenceId") Long sentenceId,
      @Param("memberId") Long memberId,
      @Param("maxSimilarity") BigDecimal maxSimilarity,
      @Param("maxCount") int maxCount);
}
