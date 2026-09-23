package com.aegispunish.velocity;

import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.database.DatabaseManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import com.aegispunish.core.skin.SkinResolver;
import com.aegispunish.velocity.listener.VelocityLoginListener;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(
        id = "aegispunish",
        name = "AegisPunish",
        version = "1.0.5",
        description = "Módulo Proxy do AegisPunish para Velocity",
        authors = {"AegisTeam"}
)
public class AegisPunishVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private DatabaseManager databaseManager;
    private PunishmentCacheManager cacheManager;
    private SkinResolver skinResolver;
    private DiscordWebhookClient discordClient;

    @Inject
    public AegisPunishVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.skinResolver = new SkinResolver(logger);

        this.databaseManager = new DatabaseManager("SQLite", dataDirectory.toFile(), "localhost", 3306, "aegispunish", "root", "", 20, 5, 1800000, 5000, logger);
        this.cacheManager = new PunishmentCacheManager(databaseManager, logger);
        this.discordClient = new DiscordWebhookClient("", skinResolver, logger);

        proxy.getEventManager().register(this, new VelocityLoginListener(
                proxy, cacheManager, discordClient, false, "bans"
        ));

        logger.info("[AegisPunish] Módulo Velocity inicializado com sucesso!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (databaseManager != null) {
            databaseManager.close();
        }
        logger.info("[AegisPunish] Módulo Velocity desativado.");
    }
}
