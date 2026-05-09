package com.notify.agent.interfaces;

/**
 * Abstraction for validating that a token is still active in the store.
 *
 * <p>
 * This is a secondary check layered on top of JWT signature / expiry
 * verification. Implementations can check a denylist (Redis), an in-memory
 * map, or simply return {@code true} for all tokens (no-op / trust-the-JWT).
 * </p>
 */
public interface TokenValidator {

    /**
     * Returns {@code true} if the given access token is considered valid
     * by this store (e.g. not revoked, exists in the backing store).
     *
     * @param accessToken raw JWT without "Bearer " prefix
     */
    boolean isAccessTokenValid(String accessToken);

    /**
     * Returns {@code true} if the given refresh token is considered valid
     * by this store (e.g. not revoked, exists in the backing store).
     *
     * @param refreshToken raw JWT without "Bearer " prefix
     */
    boolean isRefreshTokenValid(String refreshToken);
}
