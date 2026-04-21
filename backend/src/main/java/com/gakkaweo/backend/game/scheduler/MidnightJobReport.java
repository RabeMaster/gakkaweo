package com.gakkaweo.backend.game.scheduler;

public record MidnightJobReport(
    boolean expireSucceeded,
    boolean snapshotSucceeded,
    boolean keyExpireSucceeded,
    boolean selectSucceeded,
    String todaySentence,
    int yesterdayTotalPlayers,
    long unusedCount) {}
