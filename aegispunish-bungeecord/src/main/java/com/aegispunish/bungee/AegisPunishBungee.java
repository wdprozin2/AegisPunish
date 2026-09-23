package com.aegispunish.bungee;

import com.aegispunish.bungee.listener.BungeeLoginListener;
import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.database.DatabaseManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import com.aegispunish.core.skin.SkinResolver;
import net.md_5.bungee.api.plugin.Plugin;

public class AegisPunishBungee extends Plugin {

    private DatabaseManager databaseManager;
    private PunishmentCacheManager cacheManager;
    private SkinResolver skinResolver;
    private DiscordWebhookClient discordClient;

    @Override
    public void onEnable() {
        this.skinResolver = new SkinResolver(getLogger());
        this.databaseManager = new DatabaseManager("SQLite", getDataFolder(), "localhost", 3306, "aegispunish", "root", "", 20, 5, 1800000, 5000, getLogger());
        this.cacheManager = new PunishmentCacheManager(databaseManager, getLogger());
        this.discordClient = new DiscordWebhookClient("", skinResolver, getLogger());

        getProxy().getPluginManager().registerListener(this, new BungeeLoginListener(
                getProxy(), cacheManager, discordClient, false, "bans"
        ));

        getLogger().info("[AegisPunish] Módulo BungeeCord ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("[AegisPunish] Módulo BungeeCord desativado.");
    }
}
