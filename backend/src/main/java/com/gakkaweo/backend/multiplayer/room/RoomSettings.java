package com.gakkaweo.backend.multiplayer.room;

import java.util.Set;

public record RoomSettings(
    String title,
    GameMode mode,
    int rounds,
    int timeLimit,
    int maxPlayers,
    String password,
    boolean guessPublic) {

  private static final Set<Integer> ALLOWED_ROUNDS = Set.of(1, 3, 5, 7, 10);
  private static final int SENTENCE_TIME_MIN = 60;
  private static final int SENTENCE_TIME_MAX = 180;
  private static final int WORD_TIME_MIN = 30;
  private static final int WORD_TIME_MAX = 120;
  private static final int MAX_PLAYERS_MIN = 2;
  private static final int MAX_PLAYERS_MAX = 6;
  private static final int TITLE_MAX_LENGTH = 30;
  private static final int PASSWORD_MAX_LENGTH = 16;

  public void validate() {
    if (mode == null) {
      throw RoomException.settingsFailed(RoomException.Reason.INVALID_SETTINGS, "게임 모드를 선택해야 합니다");
    }
    if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
      throw RoomException.settingsFailed(
          RoomException.Reason.INVALID_SETTINGS, "방 제목은 1~30자여야 합니다");
    }
    if (!ALLOWED_ROUNDS.contains(rounds)) {
      throw RoomException.settingsFailed(
          RoomException.Reason.INVALID_SETTINGS, "라운드 수는 1, 3, 5, 7, 10 중 하나여야 합니다");
    }
    if (maxPlayers < MAX_PLAYERS_MIN || maxPlayers > MAX_PLAYERS_MAX) {
      throw RoomException.settingsFailed(
          RoomException.Reason.INVALID_SETTINGS, "최대 인원은 2~6명이어야 합니다");
    }
    validateTimeLimit();
    if (password != null && password.length() > PASSWORD_MAX_LENGTH) {
      throw RoomException.settingsFailed(
          RoomException.Reason.INVALID_SETTINGS, "비밀번호는 16자 이하여야 합니다");
    }
  }

  private void validateTimeLimit() {
    if (mode == GameMode.SENTENCE) {
      if (timeLimit < SENTENCE_TIME_MIN || timeLimit > SENTENCE_TIME_MAX) {
        throw RoomException.settingsFailed(
            RoomException.Reason.INVALID_SETTINGS, "문장 모드 제한 시간은 60~180초여야 합니다");
      }
    } else {
      if (timeLimit < WORD_TIME_MIN || timeLimit > WORD_TIME_MAX) {
        throw RoomException.settingsFailed(
            RoomException.Reason.INVALID_SETTINGS, "단어 모드 제한 시간은 30~120초여야 합니다");
      }
    }
  }

  public boolean hasPassword() {
    return password != null && !password.isEmpty();
  }
}
