package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

  Optional<GameSession> findByMemberAndSentence(Member member, DailySentence sentence);

  @Modifying
  @Query(
      "UPDATE GameSession g SET g.status ="
          + " com.gakkaweo.backend.domain.game.entity.GameSessionStatus.EXPIRED"
          + " WHERE g.sentence = :sentence"
          + " AND g.status ="
          + " com.gakkaweo.backend.domain.game.entity.GameSessionStatus.IN_PROGRESS")
  int expireInProgressSessions(@Param("sentence") DailySentence sentence);

  @Query("SELECT g FROM GameSession g JOIN FETCH g.member WHERE g.sentence = :sentence")
  List<GameSession> findAllBySentenceWithMember(@Param("sentence") DailySentence sentence);
}
