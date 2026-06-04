package com.callcenter.iam.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String refreshToken, JwtTokenProvider.TokenSubject subject, Instant expiresAt) {
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }
            redisTemplate.opsForValue().set(key(refreshToken), objectMapper.writeValueAsString(subject), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to store refresh token", ex);
        }
    }

    @Override
    public Optional<JwtTokenProvider.TokenSubject> consume(String refreshToken) {
        try {
            String key = key(refreshToken);
            String value = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, JwtTokenProvider.TokenSubject.class));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to consume refresh token", ex);
        }
    }

    @Override
    public boolean contains(String refreshToken) {
        Boolean exists = redisTemplate.hasKey(key(refreshToken));
        return Boolean.TRUE.equals(exists);
    }

    private String key(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return "iam:refresh:" + HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash refresh token", ex);
        }
    }
}
