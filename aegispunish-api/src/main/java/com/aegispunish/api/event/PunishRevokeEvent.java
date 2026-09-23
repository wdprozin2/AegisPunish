package com.aegispunish.api.event;

import java.util.UUID;

public class PunishRevokeEvent {

    private final long punishmentId;
    private final UUID revokerUuid;
    private final String revokerName;
    private final String reason;

    public PunishRevokeEvent(long punishmentId, UUID revokerUuid, String revokerName, String reason) {
        this.punishmentId = punishmentId;
        this.revokerUuid = revokerUuid;
        this.revokerName = revokerName;
        this.reason = reason;
    }

    public long getPunishmentId() { return punishmentId; }
    public UUID getRevokerUuid() { return revokerUuid; }
    public String getRevokerName() { return revokerName; }
    public String getReason() { return reason; }
}
