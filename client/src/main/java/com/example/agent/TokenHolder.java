package com.example.agent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds access token, refresh token, and expiry. Used by Dispatcher and Bootstrapper.
 * Thread-safe.
 */
public class TokenHolder {
    private final AtomicReference<String> token = new AtomicReference<>(null);
    private final AtomicReference<String> refreshToken = new AtomicReference<>(null);
    private volatile long expiresAtMs = 0;

    public String getToken() { return token.get(); }
    public String getRefreshToken() { return refreshToken.get(); }
    public boolean isExpired() { return System.currentTimeMillis() >= expiresAtMs; }
    public boolean hasToken() { return token.get() != null && !token.get().isEmpty(); }

    public void setTokens(String accessToken, String refresh, long expiresInMs) {
        token.set(accessToken);
        if (refresh != null) refreshToken.set(refresh);
        this.expiresAtMs = expiresInMs > 0
            ? System.currentTimeMillis() + expiresInMs
            : Long.MAX_VALUE;
    }

    public void setToken(String accessToken, long expiresInMs) {
        token.set(accessToken);
        this.expiresAtMs = expiresInMs > 0
            ? System.currentTimeMillis() + expiresInMs
            : Long.MAX_VALUE;
    }
}
