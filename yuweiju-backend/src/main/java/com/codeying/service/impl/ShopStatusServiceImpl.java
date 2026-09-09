package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.service.ShopStatusService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Shop Status Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
@Profile("prod")
public class ShopStatusServiceImpl implements ShopStatusService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopStatusServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public int getStatus() {
        try {
            String value = stringRedisTemplate.opsForValue().get(RedisKeys.shopStatusKey());
            if (!StringUtils.hasText(value)) return 1;
            return "0".equals(value) ? 0 : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    @Override
    public void setStatus(int status) {
        try {
            stringRedisTemplate.opsForValue().set(RedisKeys.shopStatusKey(), status == 0 ? "0" : "1");
        } catch (Exception ignored) {
        }
    }
}
