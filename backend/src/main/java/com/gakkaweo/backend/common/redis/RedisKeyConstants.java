package com.gakkaweo.backend.common.redis;

import java.time.LocalDate;
import java.util.UUID;

public final class RedisKeyConstants {

  public static final String BLACKLIST_PREFIX = "blacklist:jti:";
  public static final String RANKING_KEY_PREFIX = "ranking:";
  public static final String RANKING_DETAIL_PREFIX = "ranking_detail:";
  public static final String RANKING_MEMBER_PREFIX = "member:";
  public static final String SIMILARITY_CACHE_PREFIX = "similarity:";

  private RedisKeyConstants() {}

  public static String rankingDetailKey(LocalDate date, UUID memberPublicId) {
    return RANKING_DETAIL_PREFIX + date + ":" + RANKING_MEMBER_PREFIX + memberPublicId;
  }
}
