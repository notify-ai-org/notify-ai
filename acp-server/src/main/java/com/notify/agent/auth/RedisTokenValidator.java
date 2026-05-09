package com.notify.agent.auth;

import com.notify.agent.interfaces.TokenValidator;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import org.springframework.stereotype.Component;

/**
 * Redis-backed {@link TokenValidator}.
 *
 * <p>
 * A token is considered valid if its Redis key still exists (i.e. has not
 * expired or been explicitly revoked via
 * {@link RedisTokenStore#invalidateTokens}).
 * </p>
 */
@Component
public class RedisTokenValidator implements TokenValidator {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StatefulRedisConnection<String, String> redisConnection;

    public RedisTokenValidator(StatefulRedisConnection<String, String> redisConnection) {
        this.redisConnection = redisConnection;
    }

    @Override
    public boolean isAccessTokenValid(String accessToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        return sync.exists(ACCESS_PREFIX + accessToken) > 0;
    }

    @Override
    public boolean isRefreshTokenValid(String refreshToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        return sync.exists(REFRESH_PREFIX + refreshToken) > 0;
    }
}
