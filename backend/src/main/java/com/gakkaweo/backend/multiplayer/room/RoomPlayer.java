package com.gakkaweo.backend.multiplayer.room;

import java.time.Instant;
import java.util.UUID;

public class RoomPlayer {

  private final UUID publicId;
  private final String nickname;
  private final Instant joinedAt;
  private boolean ready;

  public RoomPlayer(UUID publicId, String nickname, Instant joinedAt) {
    this.publicId = publicId;
    this.nickname = nickname;
    this.joinedAt = joinedAt;
    this.ready = false;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public String getNickname() {
    return nickname;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public boolean isReady() {
    return ready;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  public void toggleReady() {
    this.ready = !this.ready;
  }
}
