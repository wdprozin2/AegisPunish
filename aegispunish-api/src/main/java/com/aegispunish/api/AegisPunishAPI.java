package com.aegispunish.api;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.api.model.PunishmentType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AegisPunishAPI {

    CompletableFuture<Punishment> applyPunishment(UUID targetUuid, String targetName, String targetIp,
                                                  UUID punisherUuid, String punisherName,
                                                  PunishmentType type, String reason, String proof,
                                                  String serverScope, Long durationMillis, boolean proofBypass);

    CompletableFuture<Boolean> revokePunishment(long punishmentId, UUID revokerUuid, String revokerName, String reason);

    CompletableFuture<Boolean> revokeByTarget(String target, boolean isBan, UUID revokerUuid, String revokerName, String reason);

    Optional<Punishment> getActiveBan(UUID uuid, String ip, String serverScope);

    Optional<Punishment> getActiveBan(UUID uuid, String ip, String name, String serverScope);

    boolean isMuted(UUID uuid);

    int getWarnCount(UUID uuid);
}
