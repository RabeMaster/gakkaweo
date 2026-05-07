package com.gakkaweo.backend.auth.jwt;

import com.gakkaweo.backend.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private final SecretKey secretKey;
  private final long accessExpirationMillis;
  private final Clock clock;

  public JwtProvider(JwtProperties jwtProperties, Clock clock) {
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.accessSecret());
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    this.accessExpirationMillis = jwtProperties.accessExpiration().toMillis();
    this.clock = clock;
  }

  public String createAccessToken(UUID publicId, String role) {
    Date now = Date.from(clock.instant());
    Date expiry = new Date(now.getTime() + accessExpirationMillis);

    return Jwts.builder()
        .subject(publicId.toString())
        .id(UUID.randomUUID().toString())
        .claim("role", role)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  public Claims parseAccessToken(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean validateAccessToken(String token) {
    try {
      parseAccessToken(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
