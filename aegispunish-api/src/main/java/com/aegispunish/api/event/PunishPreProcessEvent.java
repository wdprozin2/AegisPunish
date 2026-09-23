package com.aegispunish.api.event;

import com.aegispunish.api.model.PunishmentType;

import java.util.UUID;

public class PunishPreProcessEvent {

    private final UUID targetUuid;
    private final String targetName;
    private final String targetIp;
    private final UUID punisherUuid;
    private final String punisherName;
    private PunishmentType type;
    private String reason;
    private String proof;
    private boolean cancelled = false;

    public PunishPreProcessEvent(UUID targetUuid, String targetName, String targetIp,
                                 UUID punisherUuid, String punisherName,
                                 PunishmentType type, String reason, String proof) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetIp = targetIp;
        this.punisherUuid = punisherUuid;
        this.punisherName = punisherName;
        this.type = type;
        this.reason = reason;
        this.proof = proof;
    }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public String getTargetIp() { return targetIp; }
    public UUID getPunisherUuid() { return punisherUuid; }
    public String getPunisherName() { return punisherName; }
    public PunishmentType getType() { return type; }
    public void setType(PunishmentType type) { this.type = type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getProof() { return proof; }
    public void setProof(String proof) { this.proof = proof; }
}
