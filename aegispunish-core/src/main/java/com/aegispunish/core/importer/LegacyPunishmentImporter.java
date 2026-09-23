package com.aegispunish.core.importer;

import com.aegispunish.api.model.PunishmentType;
import com.aegispunish.core.database.DatabaseManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class LegacyPunishmentImporter {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final SimpleDateFormat vanillaDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    public LegacyPunishmentImporter(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public CompletableFuture<Integer> importVanilla(File serverRootDir) {
        return CompletableFuture.supplyAsync(() -> {
            int importedCount = 0;
            File bannedPlayersFile = new File(serverRootDir, "banned-players.json");
            File bannedIpsFile = new File(serverRootDir, "banned-ips.json");

            String sql = """
                INSERT INTO punishments 
                (target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, reason, server_scope, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'GLOBAL', ?, ?, TRUE)
            """;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                if (bannedPlayersFile.exists()) {
                    JsonArray playersArray = JsonParser.parseReader(new FileReader(bannedPlayersFile)).getAsJsonArray();
                    for (JsonElement elem : playersArray) {
                        JsonObject obj = elem.getAsJsonObject();
                        String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
                        String name = obj.has("name") ? obj.get("name").getAsString() : "Desconhecido";
                        String createdStr = obj.has("created") ? obj.get("created").getAsString() : null;
                        String source = obj.has("source") ? obj.get("source").getAsString() : "Console";
                        String expiresStr = obj.has("expires") ? obj.get("expires").getAsString() : "forever";
                        String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Banido via Vanilla";

                        Timestamp createdAt = createdStr != null ? parseDate(createdStr) : Timestamp.from(Instant.now());
                        Timestamp expiresAt = (expiresStr == null || expiresStr.equalsIgnoreCase("forever")) ? null : parseDate(expiresStr);
                        PunishmentType type = (expiresAt == null) ? PunishmentType.BAN : PunishmentType.TEMPBAN;

                        ps.setString(1, uuid);
                        ps.setString(2, name);
                        ps.setString(3, null);
                        ps.setString(4, UUID.nameUUIDFromBytes(source.getBytes()).toString());
                        ps.setString(5, source);
                        ps.setString(6, type.name());
                        ps.setString(7, reason);
                        ps.setTimestamp(8, createdAt);
                        ps.setTimestamp(9, expiresAt);

                        ps.addBatch();
                        importedCount++;
                    }
                }

                if (bannedIpsFile.exists()) {
                    JsonArray ipsArray = JsonParser.parseReader(new FileReader(bannedIpsFile)).getAsJsonArray();
                    for (JsonElement elem : ipsArray) {
                        JsonObject obj = elem.getAsJsonObject();
                        String ip = obj.has("ip") ? obj.get("ip").getAsString() : null;
                        String createdStr = obj.has("created") ? obj.get("created").getAsString() : null;
                        String source = obj.has("source") ? obj.get("source").getAsString() : "Console";
                        String expiresStr = obj.has("expires") ? obj.get("expires").getAsString() : "forever";
                        String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Banido por IP via Vanilla";

                        Timestamp createdAt = createdStr != null ? parseDate(createdStr) : Timestamp.from(Instant.now());
                        Timestamp expiresAt = (expiresStr == null || expiresStr.equalsIgnoreCase("forever")) ? null : parseDate(expiresStr);
                        PunishmentType type = (expiresAt == null) ? PunishmentType.IPBAN : PunishmentType.TEMPIPBAN;

                        ps.setString(1, null);
                        ps.setString(2, "IP-" + ip);
                        ps.setString(3, ip);
                        ps.setString(4, UUID.nameUUIDFromBytes(source.getBytes()).toString());
                        ps.setString(5, source);
                        ps.setString(6, type.name());
                        ps.setString(7, reason);
                        ps.setTimestamp(8, createdAt);
                        ps.setTimestamp(9, expiresAt);

                        ps.addBatch();
                        importedCount++;
                    }
                }

                ps.executeBatch();
                logger.info("[AegisPunish] Sucesso: " + importedCount + " punições do Minecraft Vanilla importadas!");
            } catch (Exception e) {
                logger.severe("[AegisPunish] Falha na importação do Vanilla: " + e.getMessage());
            }

            return importedCount;
        });
    }

    public CompletableFuture<Integer> importEssentials(File essentialsUserDataDir) {
        return CompletableFuture.supplyAsync(() -> {
            int importedCount = 0;
            if (!essentialsUserDataDir.exists() || !essentialsUserDataDir.isDirectory()) {
                logger.warning("[AegisPunish] Diretório de userdata do EssentialsX não encontrado!");
                return 0;
            }

            File[] userFiles = essentialsUserDataDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (userFiles == null) return 0;

            String punishSql = """
                INSERT INTO punishments 
                (target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, reason, server_scope, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, 'EssentialsImport', ?, ?, 'GLOBAL', CURRENT_TIMESTAMP, ?, TRUE)
            """;

            String ipHistorySql = databaseManager.isSqlite() ?
                "INSERT OR REPLACE INTO ip_history (uuid, last_known_name, ip_address) VALUES (?, ?, ?)" :
                """
                INSERT INTO ip_history (uuid, last_known_name, ip_address)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE last_known_name = VALUES(last_known_name)
                """;

            Yaml yaml = new Yaml();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement psPunish = conn.prepareStatement(punishSql);
                 PreparedStatement psIp = conn.prepareStatement(ipHistorySql)) {

                for (File file : userFiles) {
                    try (InputStream in = new FileInputStream(file)) {
                        Map<String, Object> data = yaml.load(in);
                        if (data == null) continue;

                        String uuidStr = file.getName().replace(".yml", "");
                        String lastAccountName = String.valueOf(data.getOrDefault("lastAccountName", "Desconhecido"));
                        String ipAddress = data.containsKey("ipAddress") ? String.valueOf(data.get("ipAddress")) : null;

                        if (ipAddress != null && !ipAddress.isBlank()) {
                            psIp.setString(1, uuidStr);
                            psIp.setString(2, lastAccountName);
                            psIp.setString(3, ipAddress);
                            psIp.addBatch();
                        }

                        if (data.containsKey("ban")) {
                            Object banObj = data.get("ban");
                            String reason = "Banido via Essentials";
                            long timeout = 0L;
                            if (banObj instanceof Map<?, ?> banMap) {
                                Object r = banMap.get("reason");
                                if (r != null) {
                                    reason = r.toString();
                                }
                                if (banMap.containsKey("timeout")) {
                                    timeout = ((Number) banMap.get("timeout")).longValue();
                                }
                            }

                            Timestamp expiresAt = timeout > 0 ? new Timestamp(timeout) : null;
                            PunishmentType type = expiresAt == null ? PunishmentType.BAN : PunishmentType.TEMPBAN;

                            psPunish.setString(1, uuidStr);
                            psPunish.setString(2, lastAccountName);
                            psPunish.setString(3, ipAddress);
                            psPunish.setString(4, UUID.randomUUID().toString());
                            psPunish.setString(5, type.name());
                            psPunish.setString(6, reason);
                            psPunish.setTimestamp(7, expiresAt);
                            psPunish.addBatch();
                            importedCount++;
                        }

                        if (Boolean.TRUE.equals(data.get("muted"))) {
                            long muteTimeout = 0L;
                            if (data.containsKey("timestamps") && data.get("timestamps") instanceof Map<?, ?> tsMap) {
                                if (tsMap.containsKey("mute")) {
                                    muteTimeout = ((Number) tsMap.get("mute")).longValue();
                                }
                            }
                            Timestamp expiresAt = muteTimeout > 0 ? new Timestamp(muteTimeout) : null;
                            PunishmentType type = expiresAt == null ? PunishmentType.MUTE : PunishmentType.TEMPMUTE;

                            psPunish.setString(1, uuidStr);
                            psPunish.setString(2, lastAccountName);
                            psPunish.setString(3, ipAddress);
                            psPunish.setString(4, UUID.randomUUID().toString());
                            psPunish.setString(5, type.name());
                            psPunish.setString(6, "Silenciado via Essentials");
                            psPunish.setTimestamp(7, expiresAt);
                            psPunish.addBatch();
                            importedCount++;
                        }
                    } catch (Exception ex) {
                        logger.warning("Falha ao ler userdata Essentials " + file.getName() + ": " + ex.getMessage());
                    }
                }

                psIp.executeBatch();
                psPunish.executeBatch();
                logger.info("[AegisPunish] Sucesso: " + importedCount + " punições do EssentialsX importadas!");
            } catch (Exception e) {
                logger.severe("[AegisPunish] Falha na importação do Essentials: " + e.getMessage());
            }

            return importedCount;
        });
    }

    public CompletableFuture<Integer> importLiteBans(File liteBansDir) {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            Connection sourceConn = resolveConnection(liteBansDir, "litebans.sqlite");
            if (sourceConn == null) {
                logger.warning("[AegisPunish] Banco do LiteBans não encontrado (verificado SQLite em plugins/LiteBans/ e MySQL ativo)!");
                return 0;
            }

            try (sourceConn; Connection targetConn = databaseManager.getConnection()) {
                
                String ipSql = databaseManager.isSqlite() ?
                        "INSERT OR REPLACE INTO ip_history (uuid, last_known_name, ip_address) VALUES (?, ?, ?)" :
                        "INSERT INTO ip_history (uuid, last_known_name, ip_address) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE last_known_name = VALUES(last_known_name)";
                try (Statement st = sourceConn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT uuid, name, ip FROM litebans_history");
                     PreparedStatement psIp = targetConn.prepareStatement(ipSql)) {
                    while (rs.next()) {
                        psIp.setString(1, rs.getString("uuid"));
                        psIp.setString(2, rs.getString("name"));
                        psIp.setString(3, rs.getString("ip"));
                        psIp.addBatch();
                    }
                    psIp.executeBatch();
                } catch (Exception ignored) {}

                count += importLiteBansTable(sourceConn, targetConn, "litebans_bans", PunishmentType.BAN, PunishmentType.TEMPBAN);
                count += importLiteBansTable(sourceConn, targetConn, "litebans_mutes", PunishmentType.MUTE, PunishmentType.TEMPMUTE);
                count += importLiteBansTable(sourceConn, targetConn, "litebans_warnings", PunishmentType.WARN, PunishmentType.WARN);
                count += importLiteBansTable(sourceConn, targetConn, "litebans_kicks", PunishmentType.KICK, PunishmentType.KICK);

                logger.info("[AegisPunish] Sucesso: " + count + " punições do LiteBans importadas!");
            } catch (Exception e) {
                logger.severe("[AegisPunish] Erro ao importar do LiteBans: " + e.getMessage());
            }

            return count;
        });
    }

    private int importLiteBansTable(Connection source, Connection target, String tableName,
                                    PunishmentType permType, PunishmentType tempType) {
        int imported = 0;
        String query = "SELECT uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, server_scope, active FROM " + tableName;
        String insertSql = """
            INSERT INTO punishments 
            (target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, reason, server_scope, created_at, expires_at, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery(query);
             PreparedStatement ps = target.prepareStatement(insertSql)) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String ip = rs.getString("ip");
                String reason = rs.getString("reason");
                String punisherUuid = rs.getString("banned_by_uuid");
                String punisherName = rs.getString("banned_by_name");
                long time = rs.getLong("time");
                long until = rs.getLong("until");
                String scope = rs.getString("server_scope");
                boolean active = rs.getBoolean("active");

                Timestamp createdAt = new Timestamp(time);
                Timestamp expiresAt = (until <= 0 || until == -1) ? null : new Timestamp(until);
                PunishmentType type = (expiresAt != null && tempType != null) ? tempType : permType;

                ps.setString(1, uuid);
                ps.setString(2, uuid != null ? uuid.substring(0, Math.min(uuid.length(), 16)) : (ip != null ? ip : "Desconhecido"));
                ps.setString(3, ip);
                ps.setString(4, punisherUuid != null ? punisherUuid : UUID.randomUUID().toString());
                ps.setString(5, punisherName != null ? punisherName : "Console");
                ps.setString(6, type.name());
                ps.setString(7, reason != null ? reason : "Punição LiteBans");
                ps.setString(8, scope != null ? scope : "GLOBAL");
                ps.setTimestamp(9, createdAt);
                ps.setTimestamp(10, expiresAt);
                ps.setBoolean(11, active);

                ps.addBatch();
                imported++;
            }
            ps.executeBatch();
        } catch (Exception e) {
            logger.warning("Aviso ao ler tabela " + tableName + " do LiteBans: " + e.getMessage());
        }
        return imported;
    }

    public CompletableFuture<Integer> importAdvancedBan(File advancedBanDir) {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            Connection sourceConn = resolveConnection(advancedBanDir, "AdvancedBan.db");
            if (sourceConn == null) {
                logger.warning("[AegisPunish] Banco do AdvancedBan não encontrado (verificado SQLite em plugins/AdvancedBan/ e MySQL ativo)!");
                return 0;
            }

            String insertSql = """
                INSERT INTO punishments 
                (target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, reason, server_scope, created_at, expires_at, active)
                VALUES (?, ?, NULL, ?, ?, ?, ?, 'GLOBAL', ?, ?, ?)
            """;

            try (sourceConn; Connection targetConn = databaseManager.getConnection();
                 Statement st = sourceConn.createStatement();
                 PreparedStatement ps = targetConn.prepareStatement(insertSql)) {

                for (String table : List.of("Punishments", "PunishmentHistory")) {
                    try (ResultSet rs = st.executeQuery("SELECT name, uuid, reason, operator, punishmentType, start, end FROM " + table)) {
                        while (rs.next()) {
                            String name = rs.getString("name");
                            String uuid = rs.getString("uuid");
                            String reason = rs.getString("reason");
                            String operator = rs.getString("operator");
                            String advType = rs.getString("punishmentType");
                            long start = rs.getLong("start");
                            long end = rs.getLong("end");

                            Timestamp createdAt = new Timestamp(start);
                            Timestamp expiresAt = (end <= 0 || end == -1) ? null : new Timestamp(end);

                            PunishmentType type = switch (advType.toUpperCase()) {
                                case "IP_BAN" -> PunishmentType.IPBAN;
                                case "TEMP_IP_BAN" -> PunishmentType.TEMPIPBAN;
                                case "MUTE" -> PunishmentType.MUTE;
                                case "TEMP_MUTE" -> PunishmentType.TEMPMUTE;
                                case "WARNING", "TEMP_WARNING" -> PunishmentType.WARN;
                                case "KICK" -> PunishmentType.KICK;
                                default -> expiresAt == null ? PunishmentType.BAN : PunishmentType.TEMPBAN;
                            };

                            boolean active = table.equals("Punishments");

                            ps.setString(1, uuid);
                            ps.setString(2, name != null ? name : "Desconhecido");
                            ps.setString(3, UUID.nameUUIDFromBytes(operator.getBytes()).toString());
                            ps.setString(4, operator);
                            ps.setString(5, type.name());
                            ps.setString(6, reason != null ? reason : "Importado do AdvancedBan");
                            ps.setTimestamp(7, createdAt);
                            ps.setTimestamp(8, expiresAt);
                            ps.setBoolean(9, active);

                            ps.addBatch();
                            count++;
                        }
                    } catch (Exception ignored) {}
                }

                ps.executeBatch();
                logger.info("[AegisPunish] Sucesso: " + count + " punições do AdvancedBan importadas!");
            } catch (Exception e) {
                logger.severe("[AegisPunish] Erro ao importar do AdvancedBan: " + e.getMessage());
            }

            return count;
        });
    }

    public CompletableFuture<Integer> importLeafPunish(File leafPunishDir) {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            Connection sourceConn = resolveConnection(leafPunishDir, "database.db");
            if (sourceConn == null) {
                
                sourceConn = resolveConnection(leafPunishDir, "leaf.db");
            }

            if (sourceConn == null) {
                logger.warning("[AegisPunish] Banco do LeafPunish não encontrado (verificado SQLite em plugins/LeafPunish/ e MySQL ativo)!");
                return 0;
            }

            String insertSql = """
                INSERT INTO punishments 
                (target_uuid, target_name, target_ip, punisher_uuid, punisher_name, type, reason, proof, server_scope, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            final Connection connToUse = sourceConn;
            try (connToUse; Connection targetConn = databaseManager.getConnection();
                 Statement st = connToUse.createStatement();
                 PreparedStatement ps = targetConn.prepareStatement(insertSql)) {

                String selectQuery = "SELECT * FROM punishments";
                try (ResultSet rs = st.executeQuery(selectQuery)) {
                    while (rs.next()) {
                        String uuid = rs.getString("target_uuid");
                        String name = rs.getString("target_name");
                        String ip = rs.getString("target_ip");
                        String punisherUuid = rs.getString("punisher_uuid");
                        String punisherName = rs.getString("punisher_name");
                        String typeStr = rs.getString("type");
                        String reason = rs.getString("reason");
                        String proof = rs.getString("proof");
                        String scope = rs.getString("server_scope");
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        Timestamp expiresAt = rs.getTimestamp("expires_at");
                        boolean active = rs.getBoolean("active");

                        PunishmentType type;
                        try {
                            type = PunishmentType.valueOf(typeStr.toUpperCase());
                        } catch (Exception ex) {
                            type = expiresAt == null ? PunishmentType.BAN : PunishmentType.TEMPBAN;
                        }

                        ps.setString(1, uuid);
                        ps.setString(2, name != null ? name : "Desconhecido");
                        ps.setString(3, ip);
                        ps.setString(4, punisherUuid != null ? punisherUuid : UUID.randomUUID().toString());
                        ps.setString(5, punisherName != null ? punisherName : "Console");
                        ps.setString(6, type.name());
                        ps.setString(7, reason != null ? reason : "Punição LeafPunish");
                        ps.setString(8, proof);
                        ps.setString(9, scope != null ? scope : "GLOBAL");
                        ps.setTimestamp(10, createdAt != null ? createdAt : Timestamp.from(Instant.now()));
                        ps.setTimestamp(11, expiresAt);
                        ps.setBoolean(12, active);

                        ps.addBatch();
                        count++;
                    }
                }

                ps.executeBatch();
                logger.info("[AegisPunish] Sucesso: " + count + " punições do LeafPunish importadas!");
            } catch (Exception e) {
                logger.severe("[AegisPunish] Erro ao importar do LeafPunish: " + e.getMessage());
            }

            return count;
        });
    }

    private Connection resolveConnection(File pluginDir, String sqliteFileName) {
        try {
            if (pluginDir != null && pluginDir.exists()) {
                File sqliteFile = new File(pluginDir, sqliteFileName);
                if (sqliteFile.exists()) {
                    Class.forName("org.sqlite.JDBC");
                    return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                }
            }
            
            return databaseManager.getConnection();
        } catch (Exception e) {
            return null;
        }
    }

    private Timestamp parseDate(String dateStr) {
        try {
            Date d = vanillaDateFormat.parse(dateStr);
            return new Timestamp(d.getTime());
        } catch (Exception e) {
            return Timestamp.from(Instant.now());
        }
    }
}
