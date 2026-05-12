package com.bvb.sharedmodule.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JWTTokenProvider {
    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final StringRedisTemplate redis;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    public JWTTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long accessTokenTtl,
            @Value("${jwt.refresh-expiration}") long refreshTokenTtl,
            StringRedisTemplate redis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtl);
        this.refreshTokenTtl = Duration.ofSeconds(refreshTokenTtl);
        this.redis = redis;
    }

    private String buildToken (String username, String role, Duration Ttl, String type)
    {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("username", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Ttl)))
                .signWith(secretKey)
                .compact();
    }

    public String generateAccessToken (String username, String role) {
        return buildToken(username, role, accessTokenTtl, "access");
    }

    public String generateRefreshToken (String username, String role) {
        return buildToken(username, role, refreshTokenTtl, "refresh");
    }

    public Claims getClaim (String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public void blacklistAll(List<String> tokens) {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            tokens.stream()
                    .filter(StringUtils::hasText)
                    .forEach(token -> {
                        try {
                            Claims claims = getClaim(token);
                            Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
                            if (!remaining.isNegative()) {
                                byte[] key = (BLACKLIST_PREFIX + claims.getId()).getBytes();
                                connection.stringCommands().set(key, "1".getBytes());
                                connection.keyCommands().expire(key, remaining.getSeconds());
                            }
                        } catch (JwtException e) {
                            log.warn("Could not blacklist token: {}", e.getMessage());
                        }
                    });
            return null;
        });
    }

    public String extractUsername(String token) {
        return getClaim(token).getSubject();
    }

    public String extractType(String token) {
        return getClaim(token).get("type", String.class);
    }

    public boolean isBlacklisted(String jti) {
        return redis.hasKey(BLACKLIST_PREFIX + jti);
    }
}
