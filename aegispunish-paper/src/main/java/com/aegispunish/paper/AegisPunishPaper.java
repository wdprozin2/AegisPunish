package com.aegispunish.paper;

import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.database.DatabaseManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import com.aegispunish.core.importer.LegacyPunishmentImporter;
import com.aegispunish.core.ladder.ProgressivePunishLadder;
import com.aegispunish.core.manager.PunishmentManager;
import com.aegispunish.core.skin.SkinResolver;
import com.aegispunish.core.util.ProofValidator;
import com.aegispunish.paper.command.AegisPunishAdminCommand;
import com.aegispunish.paper.command.CommandOverrideManager;
import com.aegispunish.paper.command.PunishCommand;
import com.aegispunish.paper.hierarchy.HierarchyManager;
import com.aegispunish.paper.hook.PlaceholderAPIHook;
import com.aegispunish.paper.listener.AsyncChatListener;
import com.aegispunish.paper.listener.AsyncPlayerPreLoginListener;
import com.aegispunish.paper.task.StatusBroadcastTask;
import com.aegispunish.paper.update.AutoUpdateManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class AegisPunishPaper extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PunishmentCacheManager cacheManager;
    private PunishmentManager punishmentManager;
    private SkinResolver skinResolver;
    private DiscordWebhookClient discordClient;
    private CommandOverrideManager commandOverrideManager;
    private HierarchyManager hierarchyManager;
    private AutoUpdateManager autoUpdateManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.skinResolver = new SkinResolver(getLogger());

        String type = getConfig().getString("database.type", "SQLite");
        String host = getConfig().getString("database.mysql.host", "localhost");
        int port = getConfig().getInt("database.mysql.port", 3306);
        String database = getConfig().getString("database.mysql.database", "aegispunish");
        String user = getConfig().getString("database.mysql.user", "root");
        String password = getConfig().getString("database.mysql.password", "");

        int maxPool = getConfig().getInt("database.pool-settings.max-pool-size", 30);
        int minIdle = getConfig().getInt("database.pool-settings.min-idle", 10);
        long maxLifetime = getConfig().getLong("database.pool-settings.max-lifetime", 1800000);
        long timeout = getConfig().getLong("database.pool-settings.connection-timeout", 5000);

        this.databaseManager = new DatabaseManager(type, getDataFolder(), host, port, database, user, password, maxPool, minIdle, maxLifetime, timeout, getLogger());
        this.cacheManager = new PunishmentCacheManager(databaseManager, getLogger());

        String webhookUrl = getConfig().getString("discord.webhookURL", "");
        this.discordClient = new DiscordWebhookClient(webhookUrl, skinResolver, getLogger());

        ProgressivePunishLadder ladder = new ProgressivePunishLadder();
        ladder.registerReason("Hack", List.of("ban 60d", "ipban"));
        ladder.registerReason("Flood", List.of("mute 1h", "mute 5h", "mute 10h"));
        ladder.registerReason("Ofensas", List.of("mute 1h", "mute 10h", "mute 3d"));

        List<String> proofDomains = getConfig().getStringList("proof-domains");
        ProofValidator proofValidator = new ProofValidator(proofDomains);

        this.punishmentManager = new PunishmentManager(databaseManager, cacheManager, discordClient, null, ladder, proofValidator, getLogger());

        this.hierarchyManager = new HierarchyManager(getConfig());
        this.autoUpdateManager = new AutoUpdateManager(this, getConfig());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook(punishmentManager).register();
        }

        this.commandOverrideManager = new CommandOverrideManager(punishmentManager, getLogger());
        commandOverrideManager.hijackCommandMap();
        Bukkit.getPluginManager().registerEvents(commandOverrideManager, this);

        PunishCommand punishCmd = new PunishCommand(punishmentManager, skinResolver, hierarchyManager);
        for (String cmd : List.of("punir", "ban", "tempban", "ipban", "tempipban", "mute", "tempmute", "warn", "kick", "unban", "unmute", "pardon", "historic")) {
            var pluginCmd = getCommand(cmd);
            if (pluginCmd != null) {
                pluginCmd.setExecutor(punishCmd);
                pluginCmd.setTabCompleter(punishCmd);
            }
        }

        LegacyPunishmentImporter importer = new LegacyPunishmentImporter(databaseManager, getLogger());
        AegisPunishAdminCommand adminExecutor = new AegisPunishAdminCommand(this, importer, autoUpdateManager);
        var adminCmd = getCommand("aegispunish");
        if (adminCmd != null) {
            adminCmd.setExecutor(adminExecutor);
            adminCmd.setTabCompleter(adminExecutor);
        }
        var aegisCmd = getCommand("aegis");
        if (aegisCmd != null) {
            aegisCmd.setExecutor(adminExecutor);
            aegisCmd.setTabCompleter(adminExecutor);
        }

        Bukkit.getPluginManager().registerEvents(new AsyncChatListener(punishmentManager), this);
        Bukkit.getPluginManager().registerEvents(new AsyncPlayerPreLoginListener(cacheManager, discordClient), this);
        Bukkit.getPluginManager().registerEvents(autoUpdateManager, this);

        boolean broadcastEnable = getConfig().getBoolean("broadcasts.status.enable", true);
        if (broadcastEnable) {
            long delay = getConfig().getLong("broadcasts.status.delay", 30) * 60L * 20L;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, new StatusBroadcastTask(databaseManager), 20L * 60, delay);
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, punishmentManager::refreshActivePunishedNames, 20L, 20L * 60);

        int pluginId = 34230;
        new com.aegispunish.paper.metrics.Metrics(this, pluginId);

        getLogger().info("[AegisPunish] Módulo Paper/Folia ativado com sucesso!");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (autoUpdateManager != null) {
            autoUpdateManager.reloadConfig(getConfig());
        }
        if (hierarchyManager != null) {
            hierarchyManager.reload(getConfig());
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("[AegisPunish] Módulo Paper/Folia desativado.");
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public HierarchyManager getHierarchyManager() {
        return hierarchyManager;
    }

    public AutoUpdateManager getAutoUpdateManager() {
        return autoUpdateManager;
    }
}
