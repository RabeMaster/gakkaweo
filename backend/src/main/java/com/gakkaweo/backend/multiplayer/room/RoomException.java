package com.gakkaweo.backend.multiplayer.room;

import lombok.Getter;

@Getter
public class RoomException extends RuntimeException {

  private final Type type;
  private final Reason reason;

  public RoomException(Type type, Reason reason, String message) {
    super(message);
    this.type = type;
    this.reason = reason;
  }

  public static RoomException joinFailed(Reason reason, String message) {
    return new RoomException(Type.JOIN_FAILED, reason, message);
  }

  public static RoomException startFailed(Reason reason, String message) {
    return new RoomException(Type.START_FAILED, reason, message);
  }

  public static RoomException settingsFailed(Reason reason, String message) {
    return new RoomException(Type.SETTINGS_FAILED, reason, message);
  }

  public static RoomException kickFailed(Reason reason, String message) {
    return new RoomException(Type.KICK_FAILED, reason, message);
  }

  public static RoomException delegateFailed(Reason reason, String message) {
    return new RoomException(Type.DELEGATE_FAILED, reason, message);
  }

  public static RoomException readyFailed(Reason reason, String message) {
    return new RoomException(Type.READY_FAILED, reason, message);
  }

  public static RoomException createFailed(Reason reason, String message) {
    return new RoomException(Type.CREATE_FAILED, reason, message);
  }

  public static RoomException quickJoinFailed(Reason reason, String message) {
    return new RoomException(Type.QUICK_JOIN_FAILED, reason, message);
  }

  public enum Type {
    JOIN_FAILED,
    START_FAILED,
    SETTINGS_FAILED,
    KICK_FAILED,
    DELEGATE_FAILED,
    READY_FAILED,
    LEAVE_FAILED,
    QUICK_JOIN_FAILED,
    CREATE_FAILED
  }

  public enum Reason {
    WRONG_PASSWORD,
    ROOM_FULL,
    ROOM_NOT_WAITING,
    ALREADY_IN_ROOM,
    ROOM_NOT_FOUND,
    ROOM_LIMIT_REACHED,
    NOT_HOST,
    NOT_ALL_READY,
    PLAYER_NOT_FOUND,
    CANNOT_SELF,
    NO_ROOM_AVAILABLE,
    INVALID_SETTINGS
  }
}
