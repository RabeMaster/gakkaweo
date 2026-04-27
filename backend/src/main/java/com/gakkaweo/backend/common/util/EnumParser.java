package com.gakkaweo.backend.common.util;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import java.util.Locale;

public final class EnumParser {

  private EnumParser() {}

  public static <E extends Enum<E>> E parseOrThrow(
      Class<E> enumClass, String value, ErrorCode errorCode) {
    if (value == null) {
      throw new BusinessException(errorCode);
    }
    try {
      return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(errorCode);
    }
  }
}
