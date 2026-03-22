package com.gakkaweo.backend.common.redis;

public final class RedisKeyConstants {

  public static final String BLACKLIST_PREFIX = "blacklist:jti:";
  public static final String RANKING_KEY_PREFIX = "ranking:";
  public static final String RANKING_DETAIL_PREFIX = "ranking_detail:";
  public static final String RANKING_MEMBER_PREFIX = "member:";
  public static final String SIMILARITY_CACHE_PREFIX = "similarity:";

  private RedisKeyConstants() {}
}
