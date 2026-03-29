package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DailySentenceRepository
    extends JpaRepository<DailySentence, Long>, JpaSpecificationExecutor<DailySentence> {

  Optional<DailySentence> findByUsedAt(LocalDate usedAt);

  Optional<DailySentence> findByPublicId(UUID publicId);

  @Query(
      value =
          "SELECT * FROM daily_sentences WHERE used_at IS NULL AND status = 'ACTIVE' ORDER BY RANDOM() LIMIT 1",
      nativeQuery = true)
  Optional<DailySentence> findRandomUnusedSentence();

  boolean existsBySentence(String sentence);

  @Query(
      "SELECT COUNT(d) FROM DailySentence d WHERE d.usedAt IS NULL"
          + " AND d.status = com.gakkaweo.backend.domain.game.entity.DailySentenceStatus.ACTIVE")
  long countUnusedActive();

  Optional<DailySentence> findByScheduledAt(LocalDate scheduledAt);

  boolean existsByScheduledAt(LocalDate scheduledAt);
}
