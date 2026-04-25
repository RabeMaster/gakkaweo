package com.gakkaweo.backend.common.redis;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class RedisKeyConstants {

  public static final String BLACKLIST_PREFIX = "blacklist:jti:";
  public static final String RANKING_KEY_PREFIX = "ranking:";
  public static final String RANKING_DETAIL_PREFIX = "ranking_detail:";
  public static final String SIMILARITY_CACHE_PREFIX = "similarity:";

  private static final String RANKING_MEMBER_PREFIX = "member:";

  private static final Set<String> KNOWN_PREFIXES =
      Set.of(BLACKLIST_PREFIX, RANKING_KEY_PREFIX, RANKING_DETAIL_PREFIX, SIMILARITY_CACHE_PREFIX);

  private RedisKeyConstants() {}

  public static Set<String> knownPrefixes() {
    return KNOWN_PREFIXES;
  }

  public static String blacklistKey(String jti) {
    return BLACKLIST_PREFIX + jti;
  }

  public static String rankingKey(LocalDate date) {
    return RANKING_KEY_PREFIX + date;
  }

  public static String memberKey(UUID publicId) {
    return RANKING_MEMBER_PREFIX + publicId;
  }

  public static UUID extractMemberPublicId(String memberKey) {
    return UUID.fromString(memberKey.substring(RANKING_MEMBER_PREFIX.length()));
  }

  public static String rankingDetailKey(LocalDate date, UUID memberPublicId) {
    return RANKING_DETAIL_PREFIX + date + ":" + RANKING_MEMBER_PREFIX + memberPublicId;
  }

  public static String similarityCacheKey(Long sentenceId, String hash) {
    return SIMILARITY_CACHE_PREFIX + sentenceId + ":" + hash;
  }
}
