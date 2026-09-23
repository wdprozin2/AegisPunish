package com.aegispunish.core.manager;

import com.aegispunish.api.AegisPunishAPI;
import com.aegispunish.api.model.Punishment;
import com.aegispunish.api.model.PunishmentType;
import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.database.DatabaseManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import com.aegispunish.core.ladder.ProgressivePunishLadder;
import com.aegispunish.core.pubsub.CrossServerPubSub;
import com.aegispunish.core.pubsub.NetworkPayload;
import com.aegispunish.core.util.ProofValidator;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PunishmentManager implements AegisPunishAPI {

    private final DatabaseManager databaseManager;
    private final PunishmentCacheManager cacheManager;
    private final DiscordWebhookClient discordClient;
    private final CrossServerPubSub pubSub;
    private final ProgressivePunishLadder ladder;
    private final ProofValidator proofValidator;
    private final Logger logger;
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(8);

    public PunishmentManager(DatabaseManager databaseManager,
                             PunishmentCacheManager cacheManager,
                             DiscordWebhookClient discordClient,
                             CrossServerPubSub pubSub,
                             ProgressivePunishLadder ladder,
                             ProofValidator proofValidator,
                             Logger logger) {
        this.databaseManager = databaseManager;
        this.cacheManager = cacheManager;
        this.discordClient = discordClient;
        this.pubSub = pubSub;
        this.ladder = ladder;
        this.proofValidator = proofValidator;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Punishment> applyPunishment(
            UUID targetUuid,
            String targetName,
            String targetIp,
            UUID punisherUuid,
            String punisherName,
            PunishmentType requestedType,
            String reason,
            String proof,
            String serverScope,
            Long customDurationMillis,
            boolean hasProofBypass) {

        return CompletableFuture.supplyAsync(() -> {
            if (!hasProofBypass && proof != null && !proofValidator.isValid(proof)) {
                throw new IllegalArgumentException("A prova informada não pertence a um domínio permitido.");
            }

            PunishmentType effectiveType = requestedType;
            Instant expiresAt = null;

            int previousInfractions = countPreviousInfractions(targetUuid, reason);
            ProgressivePunishLadder.Tier tier = ladder.resolveTier(reason, previousInfractions + 1);

            if (tier != null) {
                effectiveType = tier.getType();
                expiresAt = tier.getDuration() != null ? Instant.now().plus(tier.getDuration()) : null;
            } else if (customDurationMillis != null && customDurationMillis > 0) {
                expiresAt = Instant.now().plusMillis(customDurationMillis);
            }

            String insertSql = "INSERT INTO punishments " +
                    "(target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, " +
                    "reason, proof, server_scope, expires_at, active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)";

            long generatedId;
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, targetUuid != null ? targetUuid.toString() : null);
                ps.setString(2, targetName);
                ps.setString(3, targetIp);
                ps.setString(4, punisherUuid != null ? punisherUuid.toString() : UUID.randomUUID().toString());
                ps.setString(5, punisherName);
                ps.setString(6, effectiveType.name());
                ps.setString(7, reason);
                ps.setString(8, proof);
                ps.setString(9, serverScope != null ? serverScope : "GLOBAL");
                ps.setTimestamp(10, expiresAt != null ? Timestamp.from(expiresAt) : null);

                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                    } else {
                        throw new SQLException("Falha ao obter ID autoincremento da punição.");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Erro ao gravar punição no banco de dados", e);
                throw new RuntimeException("Falha na persistência da punição.", e);
            }

            Punishment punishment = new Punishment(
                    generatedId, targetUuid, targetName, targetIp,
                    punisherUuid, punisherName, effectiveType, reason, proof,
                    serverScope, Instant.now(), expiresAt, true
            );

            cacheManager.put(punishment);

            if (effectiveType == PunishmentType.IPBAN || effectiveType == PunishmentType.TEMPIPBAN) {
                applyToAssociatedAccounts(targetIp, punishment);
            }

            if (pubSub != null) {
                pubSub.publish(new NetworkPayload(NetworkPayload.Action.APPLY, punishment));
            }

            if (discordClient != null) {
                discordClient.sendPunishEmbed(punishment);
            }

            return punishment;
        }, asyncPool);
    }

    @Override
    public CompletableFuture<Boolean> revokePunishment(long punishmentId, UUID revokerUuid, String revokerName, String revokeReason) {
        return CompletableFuture.supplyAsync(() -> {
            String selectSql = "SELECT * FROM punishments WHERE id = ?";
            String updateSql = "UPDATE punishments SET active = FALSE, revoked_by_uuid = ?, " +
                    "revoked_by_name = ?, revoked_reason = ?, revoked_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND active = TRUE";

            try (Connection conn = databaseManager.getConnection()) {
                String targetName = null;
                UUID targetUuid = null;
                String typeStr = "Punição";
                String reason = "";

                try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                    psSelect.setLong(1, punishmentId);
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (rs.next()) {
                            targetName = rs.getString("target_name");
                            String uStr = rs.getString("target_uuid");
                            if (uStr != null) targetUuid = UUID.fromString(uStr);
                            typeStr = rs.getString("type");
                            reason = rs.getString("reason");
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, revokerUuid != null ? revokerUuid.toString() : UUID.randomUUID().toString());
                    ps.setString(2, revokerName);
                    ps.setString(3, revokeReason);
                    ps.setLong(4, punishmentId);

                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        cacheManager.invalidateById(punishmentId);
                        if (pubSub != null) {
                            pubSub.publish(new NetworkPayload(NetworkPayload.Action.REVOKE, punishmentId));
                        }
                        if (discordClient != null && targetName != null) {
                            discordClient.sendPardonEmbed(punishmentId, targetName, targetUuid, typeStr, reason, revokerName);
                        }
                        return true;
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Erro ao revogar punição #" + punishmentId, e);
            }
            return false;
        }, asyncPool);
    }

    @Override
    public CompletableFuture<Boolean> revokeByTarget(String target, boolean isBan, UUID revokerUuid, String revokerName, String revokeReason) {
        return CompletableFuture.supplyAsync(() -> {
            String typeFilter = isBan 
                    ? " AND type IN ('BAN', 'TEMPBAN', 'IPBAN', 'TEMPIPBAN') " 
                    : " AND type IN ('MUTE', 'TEMPMUTE') ";
            String selectSql = """
                SELECT id, target_name, target_uuid, type, reason FROM punishments 
                WHERE (LOWER(target_name) = LOWER(?) OR target_ip = ? OR target_uuid = ?) 
                  AND active = TRUE 
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """ + typeFilter + """
                ORDER BY id DESC
            """;
            String updateSql = "UPDATE punishments SET active = FALSE, revoked_by_uuid = ?, " +
                    "revoked_by_name = ?, revoked_reason = ?, revoked_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ?";

            boolean anyRevoked = false;
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement psSelect = conn.prepareStatement(selectSql)) {

                psSelect.setString(1, target);
                psSelect.setString(2, target);
                psSelect.setString(3, target);

                record TargetPunish(long id, String name, UUID uuid, String type, String reason) {}
                List<TargetPunish> list = new ArrayList<>();

                try (ResultSet rs = psSelect.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String name = rs.getString("target_name");
                        String uStr = rs.getString("target_uuid");
                        UUID uuid = uStr != null ? UUID.fromString(uStr) : null;
                        String type = rs.getString("type");
                        String reason = rs.getString("reason");
                        list.add(new TargetPunish(id, name, uuid, type, reason));
                    }
                }

                if (!list.isEmpty()) {
                    try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                        for (TargetPunish tp : list) {
                            psUpdate.setString(1, revokerUuid != null ? revokerUuid.toString() : UUID.randomUUID().toString());
                            psUpdate.setString(2, revokerName);
                            psUpdate.setString(3, revokeReason);
                            psUpdate.setLong(4, tp.id);
                            psUpdate.addBatch();
                        }
                        psUpdate.executeBatch();
                    }

                    for (TargetPunish tp : list) {
                        cacheManager.invalidateById(tp.id);
                        if (pubSub != null) {
                            pubSub.publish(new NetworkPayload(NetworkPayload.Action.REVOKE, tp.id));
                        }
                        if (discordClient != null) {
                            discordClient.sendPardonEmbed(tp.id, tp.name != null ? tp.name : target, tp.uuid, tp.type, tp.reason, revokerName);
                        }
                    }
                    anyRevoked = true;
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Erro ao revogar punições para " + target, e);
            }
            return anyRevoked;
        }, asyncPool);
    }

    @Override
    public Optional<Punishment> getActiveBan(UUID uuid, String ip, String serverScope) {
        return cacheManager.findActiveBan(uuid, ip, serverScope);
    }

    @Override
    public Optional<Punishment> getActiveBan(UUID uuid, String ip, String name, String serverScope) {
        return cacheManager.findActiveBan(uuid, ip, name, serverScope);
    }

    @Override
    public boolean isMuted(UUID uuid) {
        return cacheManager.isMuted(uuid);
    }

    @Override
    public int getWarnCount(UUID uuid) {
        return cacheManager.getWarnCount(uuid);
    }

    public PunishmentCacheManager getCacheManager() {
        return cacheManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private int countPreviousInfractions(UUID targetUuid, String reason) {
        if (targetUuid == null) return 0;
        String sql = "SELECT COUNT(*) FROM punishments WHERE target_uuid = ? AND reason LIKE ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            ps.setString(2, "%" + reason + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Falha ao contar infrações anteriores", e);
        }
        return 0;
    }

    private void applyToAssociatedAccounts(String ip, Punishment rootPunish) {
        if (ip == null || ip.isBlank()) return;
        asyncPool.submit(() -> {
            String queryAlts = "SELECT uuid, last_known_name FROM ip_history WHERE ip_address = ? AND uuid != ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(queryAlts)) {
                ps.setString(1, ip);
                ps.setString(2, rootPunish.getTargetUuid() != null ? rootPunish.getTargetUuid().toString() : "");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID altUuid = UUID.fromString(rs.getString("uuid"));
                        cacheManager.registerAltBan(altUuid, rootPunish);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Erro ao processar contas alternativas para o IP: " + ip, e);
            }
        });
    }

    public java.util.Set<String> getActiveBannedNames() {
        return cacheManager.getActiveBannedNames();
    }

    public java.util.Set<String> getActiveMutedNames() {
        return cacheManager.getActiveMutedNames();
    }

    public void refreshActivePunishedNames() {
        cacheManager.refreshActivePunishedNames();
    }
}
