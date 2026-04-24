package com.gakkaweo.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Flyway 마이그레이션 통합 테스트")
class FlywayMigrationTest extends IntegrationTestBase {

  @Autowired Flyway flyway;

  @Test
  @DisplayName("마이그레이션이 하나 이상 적용됨")
  void 마이그레이션_적용완료() {
    assertThat(flyway.info().applied()).isNotEmpty();
  }

  @Test
  @DisplayName("validate 통과")
  void validate_통과() {
    flyway.validate();
  }

  @Test
  @DisplayName("pending 마이그레이션 없음")
  void pending_없음() {
    assertThat(flyway.info().pending()).isEmpty();
  }
}
