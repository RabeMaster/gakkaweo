package com.gakkaweo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionConfigTest extends IntegrationTestBase {

  @Autowired private ApplicationContext ctx;

  @Test
  @DisplayName("기본 transactionTemplate Bean은 REQUIRED 전파 설정")
  void 기본_transactionTemplate_REQUIRED_전파() {
    TransactionTemplate t = ctx.getBean("transactionTemplate", TransactionTemplate.class);
    assertThat(t.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
  }

  @Test
  @DisplayName("newTxTemplate Bean은 REQUIRES_NEW 전파 설정")
  void newTxTemplate_REQUIRES_NEW_전파() {
    TransactionTemplate t = ctx.getBean("newTxTemplate", TransactionTemplate.class);
    assertThat(t.getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  @DisplayName("TransactionTemplate 타입 후보는 정확히 2개")
  void TransactionTemplate_빈_2개() {
    assertThat(ctx.getBeansOfType(TransactionTemplate.class)).hasSize(2);
  }
}
