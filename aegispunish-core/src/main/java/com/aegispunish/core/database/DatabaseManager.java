package com.aegispunish.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private final Logger logger;
    private boolean isSqlite = false;
    private String sqliteUrl = null;

    public DatabaseManager(String type, File dataFolder, String host, int port, String database, String username, String password,
                           int maxPoolSize, int minIdle, long maxLifetime, long timeout, Logger logger) {
        this.logger = logger;

        if ("SQLite".equalsIgnoreCase(type)) {
            initSqlite(dataFolder);
        } else {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(maxPoolSize);
                config.setMinimumIdle(minIdle);
                config.setMaxLifetime(maxLifetime);
                config.setConnectionTimeout(timeout);
                config.setPoolName("AegisPunish-HikariPool");

                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");

                this.dataSource = new HikariDataSource(config);
                createTables();
                logger.info("[AegisPunish] Conectado ao MySQL com sucesso!");
            } catch (Exception ex) {
                logger.warning("[AegisPunish] Não foi possível conectar ao MySQL (" + ex.getMessage() + "). Alternando automaticamente para SQLite local para garantir o funcionamento!");
                initSqlite(dataFolder);
            }
        }
    }

    private void initSqlite(File dataFolder) {
        this.isSqlite = true;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "aegispunish.db");
        this.sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
            createTables();
            logger.info("[AegisPunish] Banco de dados SQLite ativo: " + dbFile.getName());
        } catch (Exception e) {
            logger.severe("[AegisPunish] Erro ao inicializar SQLite: " + e.getMessage());
        }
    }

    private void createTables() {
        String punishmentsTable;
        String ipHistoryTable;

        if (isSqlite) {
            punishmentsTable = """
                CREATE TABLE IF NOT EXISTS `punishments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `target_uuid` VARCHAR(36),
                    `target_name` VARCHAR(16) NOT NULL,
                    `target_ip` VARCHAR(45),
                    `punisher_uuid` VARCHAR(36) NOT NULL,
                    `punisher_name` VARCHAR(16) NOT NULL,
                    `type` VARCHAR(20) NOT NULL,
                    `reason` TEXT NOT NULL,
                    `proof` VARCHAR(255),
                    `server_scope` VARCHAR(64) DEFAULT 'GLOBAL',
                    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    `expires_at` TIMESTAMP,
                    `active` BOOLEAN DEFAULT 1,
                    `revoked_by_uuid` VARCHAR(36),
                    `revoked_by_name` VARCHAR(16),
                    `revoked_reason` TEXT,
                    `revoked_at` TIMESTAMP
                );
            """;

            ipHistoryTable = """
                CREATE TABLE IF NOT EXISTS `ip_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `uuid` VARCHAR(36) NOT NULL,
                    `last_known_name` VARCHAR(16) NOT NULL,
                    `ip_address` VARCHAR(45) NOT NULL,
                    `last_login` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (`uuid`, `ip_address`)
                );
            """;
        } else {
            punishmentsTable = """
                CREATE TABLE IF NOT EXISTS `punishments` (
                    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    `target_uuid` VARCHAR(36) NULL,
                    `target_name` VARCHAR(16) NOT NULL,
                    `target_ip` VARCHAR(45) NULL,
                    `punisher_uuid` VARCHAR(36) NOT NULL,
                    `punisher_name` VARCHAR(16) NOT NULL,
                    `type` ENUM('BAN', 'TEMPBAN', 'IPBAN', 'TEMPIPBAN', 'MUTE', 'TEMPMUTE', 'WARN', 'KICK') NOT NULL,
                    `reason` TEXT NOT NULL,
                    `proof` VARCHAR(255) NULL,
                    `server_scope` VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
                    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `expires_at` TIMESTAMP NULL DEFAULT NULL,
                    `active` BOOLEAN NOT NULL DEFAULT TRUE,
                    `revoked_by_uuid` VARCHAR(36) NULL DEFAULT NULL,
                    `revoked_by_name` VARCHAR(16) NULL DEFAULT NULL,
                    `revoked_reason` TEXT NULL DEFAULT NULL,
                    `revoked_at` TIMESTAMP NULL DEFAULT NULL,
                    PRIMARY KEY (`id`),
                    INDEX `idx_punish_lookup_uuid` (`target_uuid`, `active`, `type`, `server_scope`),
                    INDEX `idx_punish_lookup_ip` (`target_ip`, `active`, `type`, `server_scope`),
                    INDEX `idx_punish_target_name` (`target_name`, `active`),
                    INDEX `idx_punish_active_lifecycle` (`active`, `expires_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

            ipHistoryTable = """
                CREATE TABLE IF NOT EXISTS `ip_history` (
                    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    `uuid` VARCHAR(36) NOT NULL,
                    `last_known_name` VARCHAR(16) NOT NULL,
                    `ip_address` VARCHAR(45) NOT NULL,
                    `last_login` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_uuid_ip` (`uuid`, `ip_address`),
                    INDEX `idx_ip_lookup_alts` (`ip_address`, `last_login` DESC),
                    INDEX `idx_player_uuid` (`uuid`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        }

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(punishmentsTable);
            st.execute(ipHistoryTable);
        } catch (SQLException e) {
            logger.severe("[AegisPunish] Erro ao inicializar tabelas SQL: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (isSqlite) {
            return DriverManager.getConnection(sqliteUrl);
        }
        return dataSource.getConnection();
    }

    public boolean isSqlite() {
        return isSqlite;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
