package com.notify.agent.auth;

import com.notify.agent.interfaces.TokenStore;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import org.springframework.stereotype.Component;

/**
 * Redis-backed {@link TokenStore}.
 *
 * <p>
 * Access and refresh tokens are stored as Redis keys with TTL so they are
 * automatically evicted on expiry, and can be explicitly deleted on logout
 * via {@link #invalidateTokens}.
 * </p>
 */
@Component
public class RedisTokenStore implements TokenStore {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StatefulRedisConnection<String, String> redisConnection;

    public RedisTokenStore(StatefulRedisConnection<String, String> redisConnection) {
        this.redisConnection = redisConnection;
    }

    @Override
    public void storeTokens(String userId, String accessToken, String refreshToken,
            long accessTtlSeconds, long refreshTtlSeconds) {
        RedisCommands<String, String> sync = redisConnection.sync();
        sync.setex(ACCESS_PREFIX + accessToken, accessTtlSeconds, userId);
        sync.setex(REFRESH_PREFIX + refreshToken, refreshTtlSeconds, userId);
    }

    @Override
    public void invalidateTokens(String accessToken, String refreshToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        if (accessToken != null)
            sync.del(ACCESS_PREFIX + accessToken);
        if (refreshToken != null)
            sync.del(REFRESH_PREFIX + refreshToken);
    }
}
