package com.gakkaweo.backend.domain.member.service;

import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class NicknameGenerator {

  private static final int MAX_RETRIES = 3;

  private static final List<String> ADJECTIVES =
      List.of(
          "용감한", "빛나는", "조용한", "따뜻한", "재빠른", "푸른", "붉은", "하얀", "검은", "노란", "귀여운", "씩씩한", "든든한",
          "상냥한", "활발한", "느긋한", "영리한", "담대한", "고요한", "싱그러운", "깔끔한", "소중한", "반짝이는", "차분한", "유쾌한",
          "정직한", "슬기로운", "당당한", "넉넉한", "포근한", "단단한", "맑은", "시원한", "산뜻한", "부드러운", "건강한", "행복한",
          "지혜로운", "다정한", "명랑한", "성실한", "착한", "멋진", "예쁜", "똑똑한", "강한", "빠른", "높은", "넓은", "깊은");

  private static final List<String> NOUNS =
      List.of(
          "호랑이", "토끼", "여우", "곰", "사슴", "고양이", "강아지", "펭귄", "다람쥐", "부엉이", "돌고래", "수달", "판다", "코알라",
          "기린", "독수리", "참새", "두루미", "매", "올빼미", "나비", "반딧불", "꿀벌", "무당벌레", "잠자리", "별", "달", "해",
          "구름", "바람", "산", "강", "바다", "숲", "들판", "소나무", "대나무", "벚꽃", "매화", "해바라기", "돌", "불꽃", "이슬",
          "무지개", "폭포", "하늘", "새벽", "노을", "안개", "번개");

  private final MemberRepository memberRepository;

  public NicknameGenerator(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  public String generate() {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < MAX_RETRIES; i++) {
      String nickname = createRandomNickname(random);
      if (!memberRepository.existsByNickname(nickname)) {
        return nickname;
      }
    }

    return createRandomNickname(random) + "_" + random.nextInt(1000, 10000);
  }

  private String createRandomNickname(ThreadLocalRandom random) {
    return ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
        + " "
        + NOUNS.get(random.nextInt(NOUNS.size()));
  }
}
