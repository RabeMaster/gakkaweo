package com.gakkaweo.backend.admin.sort;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import java.util.Locale;
import org.springframework.data.domain.Sort;

public final class SortRequestParser {

  private SortRequestParser() {}

  public static <E extends Enum<E> & SortField> Sort parse(
      String raw, Class<E> enumType, E defaultField, Sort.Direction defaultDirection) {
    if (raw == null || raw.isBlank()) {
      return Sort.by(defaultDirection, defaultField.entityField());
    }

    String[] parts = raw.split(",", -1);
    if (parts.length < 1 || parts.length > 2) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    String fieldKey = parts[0].trim();
    if (fieldKey.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    Sort.Direction direction = defaultDirection;
    if (parts.length == 2) {
      String dirRaw = parts[1].trim().toLowerCase(Locale.ROOT);
      direction =
          switch (dirRaw) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED);
          };
    }

    E matched = null;
    for (E candidate : enumType.getEnumConstants()) {
      if (candidate.fieldKey().equalsIgnoreCase(fieldKey)) {
        matched = candidate;
        break;
      }
    }
    if (matched == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    return Sort.by(direction, matched.entityField());
  }
}
