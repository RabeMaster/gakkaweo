package com.gakkaweo.backend.auth.jwt;

import com.gakkaweo.backend.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private final SecretKey secretKey;
  private final long accessExpirationMillis;

  public JwtProvider(JwtProperties jwtProperties) {
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getAccessSecret());
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    this.accessExpirationMillis = jwtProperties.getAccessExpiration().toMillis();
  }

  public String createAccessToken(UUID publicId, String role) {
    Date now = new Date();
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
