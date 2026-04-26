package com.gakkaweo.backend.admin.sort;

public enum UserSortField implements SortField {
  CREATED_AT("createdAt", "createdAt"),
  NICKNAME("nickname", "nickname"),
  ROLE("role", "role"),
  BANNED("banned", "banned");

  private final String fieldKey;
  private final String entityField;

  UserSortField(String fieldKey, String entityField) {
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
