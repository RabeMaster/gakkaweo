package com.gakkaweo.backend.admin.sort;

public enum AuditLogSortField implements SortField {
  CREATED_AT("createdAt", "createdAt"),
  ACTION("action", "action");

  private final String fieldKey;
  private final String entityField;

  AuditLogSortField(String fieldKey, String entityField) {
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
