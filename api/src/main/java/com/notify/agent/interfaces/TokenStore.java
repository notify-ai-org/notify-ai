package com.notify.agent.interfaces;

/**
 * Abstraction for storing and invalidating JWT tokens.
 *
 * <p>
 * Implementations decide the backing store — Redis (acp-server),
 * in-memory AtomicReference (client SDK), or no-op.
 * </p>
 */
public interface TokenStore {

    /**
     * Persist an access + refresh token pair for the given user.
     *
     * @param userId            the subject / client ID the tokens belong to
     * @param accessToken       the raw JWT access token (without "Bearer " prefix)
     * @param refreshToken      the raw JWT refresh token
     * @param accessTtlSeconds  how long the access token is valid (seconds)
     * @param refreshTtlSeconds how long the refresh token is valid (seconds)
     */
    void storeTokens(String userId, String accessToken, String refreshToken,
            long accessTtlSeconds, long refreshTtlSeconds);

    /**
     * Remove both tokens from the store (logout / revocation).
     * Implementations that do not support revocation may no-op.
     *
     * @param accessToken  may be null — ignored if null
     * @param refreshToken may be null — ignored if null
     */
    void invalidateTokens(String accessToken, String refreshToken);
}
