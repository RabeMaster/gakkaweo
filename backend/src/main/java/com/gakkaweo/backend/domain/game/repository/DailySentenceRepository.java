package com.gakkaweo.backend.domain.game.repository;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySentenceRepository extends JpaRepository<DailySentence, Long> {

  Optional<DailySentence> findByUsedAt(LocalDate usedAt);
}
