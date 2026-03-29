package com.gakkaweo.backend.domain.member.validation;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class NicknameValidator {

  private static final List<String> FORBIDDEN_WORDS =
      List.of(
          "admin",
          "administrator",
          "manager",
          "moderator",
          "operator",
          "staff",
          "system",
          "official",
          "developer",
          "관리자",
          "관리인",
          "관리팀",
          "운영자",
          "운영진",
          "운영인",
          "운영팀",
          "어드민",
          "매니저",
          "스태프",
          "스탭",
          "개발자",
          "개발진",
          "개발팀",
          "공식",
          "시스템");

  public String normalize(String raw) {
    return raw.strip().replaceAll("\\s+", " ");
  }

  public void validate(String nickname) {
    if (nickname.length() < 2 || nickname.length() > 12) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    String stripped = nickname.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "");
    if (FORBIDDEN_WORDS.stream().anyMatch(stripped::contains)) {
      throw new BusinessException(ErrorCode.NICKNAME_FORBIDDEN);
    }
  }
}
