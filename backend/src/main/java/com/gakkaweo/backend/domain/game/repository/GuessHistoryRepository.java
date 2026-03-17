package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuessHistoryRepository extends JpaRepository<GuessHistory, Long> {

  List<GuessHistory> findBySessionOrderByAttemptNumberAsc(GameSession session);

  long countBySession(GameSession session);
}
