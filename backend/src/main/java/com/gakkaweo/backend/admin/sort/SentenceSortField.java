package com.gakkaweo.backend.admin.sort;

public enum SentenceSortField implements SortField {
  CREATED_AT("createdAt", "createdAt"),
  STATUS("status", "status"),
  USED_AT("usedAt", "usedAt"),
  SCHEDULED_AT("scheduledAt", "scheduledAt"),
  SENTENCE("sentence", "sentence");

  private final String fieldKey;
  private final String entityField;

  SentenceSortField(String fieldKey, String entityField) {
    this.fieldKey = fieldKey;
    this.entityField = entityField;
  }

  @Override
  public String fieldKey() {
    return fieldKey;
  }

  @Override
  public String entityField() {
    return entityField;
  }
}
