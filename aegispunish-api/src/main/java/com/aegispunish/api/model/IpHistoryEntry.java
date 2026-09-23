package com.aegispunish.api.model;

import java.time.Instant;
import java.util.UUID;

public class IpHistoryEntry {

    private long id;
    private UUID uuid;
    private String lastKnownName;
    private String ipAddress;
    private Instant lastLogin;

    public IpHistoryEntry(long id, UUID uuid, String lastKnownName, String ipAddress, Instant lastLogin) {
        this.id = id;
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.ipAddress = ipAddress;
        this.lastLogin = lastLogin;
    }

    public long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getLastKnownName() { return lastKnownName; }
    public String getIpAddress() { return ipAddress; }
    public Instant getLastLogin() { return lastLogin; }
}
