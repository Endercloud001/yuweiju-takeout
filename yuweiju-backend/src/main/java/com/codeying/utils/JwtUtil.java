package com.codeying.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类。
 *
 * @author Endercloud
 */
public class JwtUtil {

    private JwtUtil() {
    }

    /**
     * 创建 JWT。
     *
     * @param secret    密钥
     * @param ttlMillis 过期时间（毫秒）
     * @param userId    用户 ID
     * @param username  用户名（可为 openid）
     * @param tokenType token 类型（如 admin/user）
     * @return JWT 字符串
     */
    public static String createToken(String secret, long ttlMillis, Long userId, String username, String tokenType) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(ttlMillis);

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(tokenType)
                .claim("uid", userId)
                .claim("username", username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(keyFromSecret(secret), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT Claims。
     *
     * @param token  JWT
     * @param secret 密钥
     * @return Claims
     * @throws JwtException 解析失败
     */
    public static Claims parseClaims(String token, String secret) throws JwtException {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(keyFromSecret(secret))
                .build()
                .parseClaimsJws(token);
        return jws.getBody();
    }

    private static SecretKey keyFromSecret(String secret) {
        byte[] raw = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return Keys.hmacShaKeyFor(raw);
        }
        return Keys.hmacShaKeyFor(sha256(raw));
    }

    private static byte[] sha256(byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
