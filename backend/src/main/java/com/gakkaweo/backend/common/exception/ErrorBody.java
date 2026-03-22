package com.gakkaweo.backend.common.exception;

public record ErrorBody(int status, String code, String message, String timestamp) {}
