package com.notify.banking.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

@Model(description = "Payload for suspicious login detection events")
public class LoginPayload {

    @Vocabulary(name = "userId", description = "User/account ID of the login attempt")
    private String userId;

    @Vocabulary(name = "ipAddress", description = "IP address of the login attempt")
    private String ipAddress;

    @Vocabulary(name = "deviceId", description = "Device fingerprint or identifier")
    private String deviceId;

    @Vocabulary(name = "location", description = "Geo-location of the login attempt")
    private String location;

    @Vocabulary(name = "timestamp", description = "Timestamp of the login attempt (ISO-8601)")
    private String timestamp;

    public LoginPayload() {}

    public LoginPayload(String userId, String ipAddress, String deviceId, String location, String timestamp) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.deviceId = deviceId;
        this.location = location;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
