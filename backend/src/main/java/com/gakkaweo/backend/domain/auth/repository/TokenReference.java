package com.gakkaweo.backend.domain.auth.repository;

public interface TokenReference {

  Long getMemberId();

  Long getSessionId();
}
