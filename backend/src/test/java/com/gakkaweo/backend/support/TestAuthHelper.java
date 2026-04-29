package com.gakkaweo.backend.support;

import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.service.AuthService;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.LocalAccount;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class TestAuthHelper {

  private static final AtomicLong nicknameSeq = new AtomicLong();
  private static final AtomicLong usernameSeq = new AtomicLong();

  private final MemberRepository memberRepository;
  private final LocalAccountRepository localAccountRepository;
  private final DailySentenceRepository dailySentenceRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public Member createMember() {
    return createMember(MemberRole.USER, false);
  }

  public Member createAdmin() {
    return createMember(MemberRole.ADMIN, false);
  }

  public Member createSuperAdmin() {
    return createMember(MemberRole.SUPERADMIN, false);
  }

  public Member createBannedMember() {
    return createMember(MemberRole.USER, true);
  }

  public Member createMember(MemberRole role, boolean banned) {
    String nickname = "테스터" + nicknameSeq.incrementAndGet();
    return transactionTemplate.execute(
        status -> {
          Member member = new Member(nickname);
          if (role != MemberRole.USER) {
            member.setRole(role);
          }
          if (banned) {
            member.setBanned(true);
            member.setBannedAt(clock.instant());
          }
          return memberRepository.save(member);
        });
  }

  public LocalAccount createLocalAccount(Member member, String username, String rawPassword) {
    return transactionTemplate.execute(
        status ->
            localAccountRepository.save(
                new LocalAccount(member, username, passwordEncoder.encode(rawPassword))));
  }

  public String nextUsername() {
    return "user" + usernameSeq.incrementAndGet();
  }

  public TokenPair issueTokens(Member member) {
    return authService.issueTokens(member);
  }

  public HttpHeaders cookieHeaderFor(Member member) {
    TokenPair tokens = issueTokens(member);
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE,
        "access_token="
            + tokens.accessToken()
            + "; refresh_token="
            + tokens.refreshToken()
            + "; has_session=1");
    return headers;
  }

  public HttpHeaders refreshCookieHeaderFor(String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + refreshToken);
    return headers;
  }

  public HttpHeaders accessCookieHeaderFor(Member member) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "access_token=" + issueTokens(member).accessToken());
    return headers;
  }

  public String accessTokenCookieFor(Member member) {
    return "access_token=" + issueTokens(member).accessToken();
  }

  public DailySentence createTodaySentence(String sentence) {
    return transactionTemplate.execute(
        status -> {
          DailySentence s = new DailySentence(sentence);
          s.setUsedAt(LocalDate.now(clock));
          s.setStatus(DailySentenceStatus.USED);
          return dailySentenceRepository.save(s);
        });
  }

  public DailySentence createActiveSentence(String sentence) {
    return transactionTemplate.execute(
        status -> dailySentenceRepository.save(new DailySentence(sentence)));
  }

  public DailySentence createUsedSentence(String sentence, LocalDate usedAt) {
    return transactionTemplate.execute(
        status -> {
          DailySentence s = new DailySentence(sentence);
          s.setUsedAt(usedAt);
          s.setStatus(DailySentenceStatus.USED);
          return dailySentenceRepository.save(s);
        });
  }

  public DailySentence createScheduledSentence(String sentence, LocalDate scheduledAt) {
    return transactionTemplate.execute(
        status -> {
          DailySentence s = new DailySentence(sentence);
          s.setScheduledAt(scheduledAt);
          return dailySentenceRepository.save(s);
        });
  }
}
