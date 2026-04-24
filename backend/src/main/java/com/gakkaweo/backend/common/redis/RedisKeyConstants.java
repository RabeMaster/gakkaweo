package com.gakkaweo.backend.common.redis;

import java.time.LocalDate;
import java.util.UUID;

public final class RedisKeyConstants {

  private static final String BLACKLIST_PREFIX = "blacklist:jti:";
  private static final String RANKING_KEY_PREFIX = "ranking:";
  private static final String RANKING_DETAIL_PREFIX = "ranking_detail:";
  private static final String RANKING_MEMBER_PREFIX = "member:";
  private static final String SIMILARITY_CACHE_PREFIX = "similarity:";

  private RedisKeyConstants() {}

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
