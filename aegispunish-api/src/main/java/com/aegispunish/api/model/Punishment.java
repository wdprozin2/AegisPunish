package com.aegispunish.api.model;

import java.time.Instant;
import java.util.UUID;

public class Punishment {

    private long id;
    private UUID targetUuid;
    private String targetName;
    private String targetIp;
    private UUID punisherUuid;
    private String punisherName;
    private PunishmentType type;
    private String reason;
    private String proof;
    private String serverScope;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean active;
    private UUID revokedByUuid;
    private String revokedByName;
    private String revokedReason;
    private Instant revokedAt;

    public Punishment() {}

    public Punishment(long id, UUID targetUuid, String targetName, String targetIp,
                      UUID punisherUuid, String punisherName, PunishmentType type,
                      String reason, String proof, String serverScope,
                      Instant createdAt, Instant expiresAt, boolean active) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetIp = targetIp;
        this.punisherUuid = punisherUuid;
        this.punisherName = punisherName;
        this.type = type;
        this.reason = reason;
        this.proof = proof;
        this.serverScope = serverScope;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return Instant.now().isAfter(expiresAt);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public UUID getTargetUuid() { return targetUuid; }
    public void setTargetUuid(UUID targetUuid) { this.targetUuid = targetUuid; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }

    public UUID getPunisherUuid() { return punisherUuid; }
    public void setPunisherUuid(UUID punisherUuid) { this.punisherUuid = punisherUuid; }

    public String getPunisherName() { return punisherName; }
    public void setPunisherName(String punisherName) { this.punisherName = punisherName; }

    public PunishmentType getType() { return type; }
    public void setType(PunishmentType type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getProof() { return proof; }
    public void setProof(String proof) { this.proof = proof; }

    public String getServerScope() { return serverScope; }
    public void setServerScope(String serverScope) { this.serverScope = serverScope; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public UUID getRevokedByUuid() { return revokedByUuid; }
    public void setRevokedByUuid(UUID revokedByUuid) { this.revokedByUuid = revokedByUuid; }

    public String getRevokedByName() { return revokedByName; }
    public void setRevokedByName(String revokedByName) { this.revokedByName = revokedByName; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
