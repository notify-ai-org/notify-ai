package com.notify.banking.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

@Model(description = "Payload for OTP request events")
public class OtpPayload {

    @Vocabulary(name = "userId", description = "User requesting the OTP")
    private String userId;

    @Vocabulary(name = "otpCode", description = "Generated OTP code")
    private String otpCode;

    @Vocabulary(name = "channel", description = "Delivery channel (SMS, EMAIL)")
    private String channel;

    @Vocabulary(name = "expiresAt", description = "OTP expiry timestamp (ISO-8601)")
    private String expiresAt;

    public OtpPayload() {}

    public OtpPayload(String userId, String otpCode, String channel, String expiresAt) {
        this.userId = userId;
        this.otpCode = otpCode;
        this.channel = channel;
        this.expiresAt = expiresAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
