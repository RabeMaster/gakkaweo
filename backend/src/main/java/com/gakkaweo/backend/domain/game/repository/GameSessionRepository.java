package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

  Optional<GameSession> findByMemberAndSentence(Member member, DailySentence sentence);
}
