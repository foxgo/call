package com.callcenter.iam.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtTokenProvider {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final Clock clock;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenProvider(String secret, Clock clock, long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String issueAccessToken(TokenSubject subject) {
        return issueToken(subject, "access", accessTokenTtlSeconds);
    }

    public String issueRefreshToken(TokenSubject subject) {
        return issueToken(subject, "refresh", refreshTokenTtlSeconds);
    }

    public TokenClaims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid token");
            }
            String payload = parts[1];
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!expectedSignature.equals(parts[2])) {
                throw new IllegalArgumentException("invalid signature");
            }
            Map<String, Object> claims = objectMapper.readValue(URL_DECODER.decode(payload), MAP_TYPE);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now(clock).getEpochSecond() > exp) {
                throw new IllegalArgumentException("token expired");
            }
            return new TokenClaims(
                    toNullableLong(claims.get("tenantId")),
                    toLong(claims.get("userId")),
                    toLongList(claims.get("roleIds")),
                    toLongList(claims.get("deptIds")),
                    String.valueOf(claims.get("type"))
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to parse token", ex);
        }
    }

    public long refreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    private String issueToken(TokenSubject subject, String type, long ttlSeconds) {
        try {
            long now = Instant.now(clock).getEpochSecond();
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("tenantId", subject.tenantId());
            payloadMap.put("userId", subject.userId());
            payloadMap.put("roleIds", subject.roleIds());
            payloadMap.put("deptIds", subject.deptIds());
            payloadMap.put("type", type);
            payloadMap.put("jti", UUID.randomUUID().toString());
            payloadMap.put("iat", now);
            payloadMap.put("exp", now + ttlSeconds);
            String header = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            )));
            String payload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payloadMap));
            String unsignedToken = header + "." + payload;
            return unsignedToken + "." + sign(unsignedToken);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to issue token", ex);
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private Long toNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<Object>) value).stream()
                .map(item -> ((Number) item).longValue())
                .toList();
    }

    public record TokenSubject(
            Long tenantId,
            Long userId,
            List<Long> roleIds,
            List<Long> deptIds
    ) {
    }

    public record TokenClaims(
            Long tenantId,
            Long userId,
            List<Long> roleIds,
            List<Long> deptIds,
            String type
    ) {
    }
}
