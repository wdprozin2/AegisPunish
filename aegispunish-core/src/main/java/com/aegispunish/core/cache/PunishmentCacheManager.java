package com.aegispunish.core.cache;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.api.model.PunishmentType;
import com.aegispunish.core.database.DatabaseManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class PunishmentCacheManager {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    private final Cache<UUID, Punishment> activeBansByUuid = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    private final Cache<String, Punishment> activeBansByName = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    private final Cache<String, Punishment> activeBansByIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(20_000)
            .build();

    private final Cache<UUID, Punishment> activeMutesByUuid = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(30_000)
            .build();

    private final Cache<UUID, Integer> warnCounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(20_000)
            .build();

    public PunishmentCacheManager(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public void put(Punishment p) {
        if (!p.isActive() || p.isExpired()) return;

        if (p.getType().isBan()) {
            if (p.getTargetUuid() != null) {
                activeBansByUuid.put(p.getTargetUuid(), p);
            }
            if (p.getTargetIp() != null) {
                activeBansByIp.put(p.getTargetIp(), p);
            }
            if (p.getTargetName() != null && !p.getTargetName().isBlank()) {
                activeBansByName.put(p.getTargetName().toLowerCase(java.util.Locale.ROOT), p);
            }
        } else if (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMPMUTE) {
            if (p.getTargetUuid() != null) {
                activeMutesByUuid.put(p.getTargetUuid(), p);
            }
        } else if (p.getType() == PunishmentType.WARN) {
            if (p.getTargetUuid() != null) {
                warnCounts.asMap().compute(p.getTargetUuid(), (k, v) -> v == null ? 1 : v + 1);
            }
        }
    }

    public void registerAltBan(UUID altUuid, Punishment rootPunish) {
        activeBansByUuid.put(altUuid, rootPunish);
    }

    public void invalidateById(long id) {
        activeBansByUuid.asMap().values().removeIf(p -> p.getId() == id);
        activeBansByIp.asMap().values().removeIf(p -> p.getId() == id);
        activeBansByName.asMap().values().removeIf(p -> p.getId() == id);
        activeMutesByUuid.asMap().values().removeIf(p -> p.getId() == id);
    }

    public Optional<Punishment> findActiveIpBan(String ip) {
        if (ip == null || ip.isBlank()) return Optional.empty();

        Punishment cached = activeBansByIp.getIfPresent(ip);
        if (cached != null) {
            if (cached.isExpired()) {
                activeBansByIp.invalidate(ip);
            } else {
                return Optional.of(cached);
            }
        }

        return queryActiveFromDb(null, ip, null, true);
    }

    public Optional<Punishment> findActiveBan(UUID uuid, String ip, String name, String serverScope) {
        if (uuid != null) {
            Punishment cached = activeBansByUuid.getIfPresent(uuid);
            if (cached != null) {
                if (cached.isExpired()) {
                    activeBansByUuid.invalidate(uuid);
                } else if (matchesScope(cached, serverScope)) {
                    return Optional.of(cached);
                }
            }
        }

        if (name != null && !name.isBlank()) {
            Punishment cached = activeBansByName.getIfPresent(name.toLowerCase(java.util.Locale.ROOT));
            if (cached != null) {
                if (cached.isExpired()) {
                    activeBansByName.invalidate(name.toLowerCase(java.util.Locale.ROOT));
                } else if (matchesScope(cached, serverScope)) {
                    return Optional.of(cached);
                }
            }
        }

        if (ip != null && !ip.isBlank()) {
            Optional<Punishment> ipBan = findActiveIpBan(ip);
            if (ipBan.isPresent() && matchesScope(ipBan.get(), serverScope)) {
                return ipBan;
            }
        }

        return queryActiveFromDb(uuid, ip, name, true);
    }

    public Optional<Punishment> findActiveBan(UUID uuid, String ip, String serverScope) {
        return findActiveBan(uuid, ip, null, serverScope);
    }

    public boolean isMuted(UUID uuid, String name) {
        if (uuid != null) {
            Punishment cached = activeMutesByUuid.getIfPresent(uuid);
            if (cached != null) {
                if (cached.isExpired()) {
                    activeMutesByUuid.invalidate(uuid);
                    return false;
                }
                return true;
            }
        }

        Optional<Punishment> dbPunish = queryActiveFromDb(uuid, null, name, false);
        return dbPunish.isPresent();
    }

    public boolean isMuted(UUID uuid) {
        return isMuted(uuid, null);
    }

    public int getWarnCount(UUID uuid) {
        if (uuid == null) return 0;
        return warnCounts.get(uuid, k -> {
            String sql = "SELECT COUNT(*) FROM punishments WHERE target_uuid = ? AND type = 'WARN' AND active = TRUE";
            try (Connection conn = databaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (Exception e) {
                logger.warning("Falha ao buscar contagem de warns: " + e.getMessage());
            }
            return 0;
        });
    }

    public void recordIpHistory(UUID uuid, String name, String ip) {
        if (uuid == null || ip == null) return;
        String sql;
        if (databaseManager.isSqlite()) {
            sql = "INSERT OR REPLACE INTO ip_history (uuid, last_known_name, ip_address, last_login) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        } else {
            sql = """
                INSERT INTO ip_history (uuid, last_known_name, ip_address, last_login)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE last_known_name = VALUES(last_known_name), last_login = CURRENT_TIMESTAMP
            """;
        }
        try (Connection conn = databaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.fine("Falha ao gravar ip_history: " + e.getMessage());
        }
    }

    private Optional<Punishment> queryActiveFromDb(UUID uuid, String ip, String name, boolean ban) {
        String typeFilter = ban 
                ? " AND type IN ('BAN', 'TEMPBAN', 'IPBAN', 'TEMPIPBAN') " 
                : " AND type IN ('MUTE', 'TEMPMUTE') ";
        String sql = """
            SELECT * FROM punishments 
            WHERE (target_uuid = ? 
                   OR (target_ip IS NOT NULL AND target_ip != '' AND target_ip = ?) 
                   OR (target_name IS NOT NULL AND target_name != '' AND LOWER(target_name) = LOWER(?))) 
              AND active = TRUE 
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
        """ + typeFilter + """
            ORDER BY id DESC LIMIT 1
        """;

        try (Connection conn = databaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid != null ? uuid.toString() : "");
            ps.setString(2, ip != null ? ip : "");
            ps.setString(3, name != null ? name : "");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Punishment p = mapResultSet(rs);
                    put(p);
                    return Optional.of(p);
                }
            }
        } catch (Exception e) {
            logger.warning("Falha na consulta SQL de punição ativa: " + e.getMessage());
        }
        return Optional.empty();
    }

    private boolean matchesScope(Punishment p, String serverScope) {
        if (p.getServerScope() == null || p.getServerScope().equalsIgnoreCase("GLOBAL")) return true;
        return p.getServerScope().equalsIgnoreCase(serverScope);
    }

    private Punishment mapResultSet(ResultSet rs) throws Exception {
        Timestamp expTs = rs.getTimestamp("expires_at");
        return new Punishment(
                rs.getLong("id"),
                rs.getString("target_uuid") != null ? UUID.fromString(rs.getString("target_uuid")) : null,
                rs.getString("target_name"),
                rs.getString("target_ip"),
                UUID.fromString(rs.getString("punisher_uuid")),
                rs.getString("punisher_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("proof"),
                rs.getString("server_scope"),
                rs.getTimestamp("created_at").toInstant(),
                expTs != null ? expTs.toInstant() : null,
                rs.getBoolean("active")
        );
    }
}
