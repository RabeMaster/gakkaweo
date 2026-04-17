package com.gakkaweo.backend.infra.seeding;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.LocalAccount;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.service.NicknameGenerator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class InitialDataSeeder {

  private static final String SEED_SENTENCES_PATH = "seed/sentences.txt";
  private static final String ADMIN_USERNAME = "admin";
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final SeedProperties seedProperties;
  private final DailySentenceRepository dailySentenceRepository;
  private final MemberRepository memberRepository;
  private final LocalAccountRepository localAccountRepository;
  private final NicknameGenerator nicknameGenerator;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactionTemplate;

  @EventListener(ApplicationReadyEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void onApplicationReady() {
    boolean sentencesEmpty = dailySentenceRepository.count() == 0;
    boolean membersEmpty = memberRepository.count() == 0;

    if (!sentencesEmpty && !membersEmpty) {
      log.info("초기 시딩 skip: daily_sentences, members 모두 데이터 존재");
      return;
    }

    if (sentencesEmpty) {
      seedSentences();
    } else {
      log.info("daily_sentences 데이터 존재, 문장 시딩 skip");
    }

    if (membersEmpty) {
      seedAdmin();
    } else {
      log.info("members 데이터 존재, admin 시딩 skip");
    }
  }

  private void seedSentences() {
    ClassPathResource resource = new ClassPathResource(SEED_SENTENCES_PATH);
    if (!resource.exists()) {
      throw new IllegalStateException("필수 시드 파일 없음: " + SEED_SENTENCES_PATH);
    }

    Set<String> seen = new HashSet<>();
    List<DailySentence> sentences = new ArrayList<>();
    try (InputStream is = resource.getInputStream();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String sentence = line.strip();
        if (sentence.isEmpty() || sentence.startsWith("#") || !seen.add(sentence)) {
          continue;
        }
        sentences.add(new DailySentence(sentence));
      }
    } catch (IOException e) {
      throw new IllegalStateException("시드 파일 읽기 실패: " + SEED_SENTENCES_PATH, e);
    }

    if (sentences.isEmpty()) {
      throw new IllegalStateException("시드 파일에서 유효한 문장을 찾지 못함: " + SEED_SENTENCES_PATH);
    }
    transactionTemplate.executeWithoutResult(status -> dailySentenceRepository.saveAll(sentences));
    log.info("문장 시딩 완료: 삽입={}", sentences.size());
  }

  private void seedAdmin() {
    String password = seedProperties.getAdminPassword();
    if (!StringUtils.hasText(password)) {
      log.info("SEED_ADMIN_PASSWORD 미설정, admin 시딩 skip");
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException(
          "SEED_ADMIN_PASSWORD 는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다");
    }

    String passwordHash = passwordEncoder.encode(password);
    String nickname = nicknameGenerator.generate();

    transactionTemplate.executeWithoutResult(
        status -> {
          Member member = memberRepository.save(new Member(nickname));
          member.setRole(MemberRole.ADMIN);
          localAccountRepository.save(new LocalAccount(member, ADMIN_USERNAME, passwordHash));
        });

    log.info("admin 시딩 완료: nickname={}, username={}", nickname, ADMIN_USERNAME);
  }
}
