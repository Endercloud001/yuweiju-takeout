package com.codeying.security;

import com.codeying.constant.RedisKeys;
import com.codeying.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT Token 黑名单服务。
 *
 * @author Endercloud
 */
@Service
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;

    public TokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 判断 jti 是否在黑名单中。
     *
     * @param jti JWT ID
     * @return 是否在黑名单
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) return false;
        try {
            Boolean exists = stringRedisTemplate.hasKey(RedisKeys.tokenBlacklistKey(jti));
            return Boolean.TRUE.equals(exists);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 将 token 加入黑名单，过期时间与 token exp 对齐。
     *
     * @param token  JWT
     * @param secret 密钥
     */
    public void blacklist(String token, String secret) {
        Claims claims = JwtUtil.parseClaims(token, secret);
        String jti = claims.getId();
        Date exp = claims.getExpiration();
        if (jti == null || jti.isEmpty() || exp == null) return;

        long ttlMillis = exp.toInstant().toEpochMilli() - Instant.now().toEpochMilli();
        if (ttlMillis <= 0) return;

        try {
            stringRedisTemplate.opsForValue().set(RedisKeys.tokenBlacklistKey(jti), "1", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }
}
