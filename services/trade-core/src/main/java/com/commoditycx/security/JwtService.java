package com.commoditycx.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Service
public class JwtService {
 private final SecretKey key;private final long ttlMinutes;
 public JwtService(@Value("${commoditycx.security.jwt-secret}") String secret,@Value("${commoditycx.security.token-ttl-minutes:10}") long ttlMinutes){
  if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
  key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.ttlMinutes=ttlMinutes;
 }
 public String issue(UUID userId,String email,List<String> roles){
  Instant now=Instant.now();return Jwts.builder().id(UUID.randomUUID().toString()).subject(userId.toString()).claim("auth_source","commoditycx").claim("email",email).claim("roles",roles).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlMinutes*60))).signWith(key).compact();
 }
 public Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
 public UUID subject(String token){return UUID.fromString(claims(token).getSubject());}
 public String tokenId(String token){return claims(token).getId();}
 public Instant expiresAt(String token){return claims(token).getExpiration().toInstant();}
}