package com.aegispunish.bungee.listener;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.event.EventHandler;

import java.util.Optional;
import java.util.UUID;

public class BungeeLoginListener implements Listener {

    private final ProxyServer proxy;
    private final PunishmentCacheManager cacheManager;
    private final DiscordWebhookClient discordClient;
    private final boolean banServerEnabled;
    private final String banServerName;

    public BungeeLoginListener(ProxyServer proxy, PunishmentCacheManager cacheManager,
                               DiscordWebhookClient discordClient, boolean banServerEnabled, String banServerName) {
        this.proxy = proxy;
        this.cacheManager = cacheManager;
        this.discordClient = discordClient;
        this.banServerEnabled = banServerEnabled;
        this.banServerName = banServerName;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        UUID uuid = connection.getUniqueId();
        String ip = connection.getAddress().getAddress().getHostAddress();

        cacheManager.recordIpHistory(uuid, connection.getName(), ip);

        Optional<Punishment> activeBan = cacheManager.findActiveBan(uuid, ip, connection.getName(), "GLOBAL");
        if (activeBan.isPresent()) {
            Punishment p = activeBan.get();

            if (banServerEnabled) {
                ServerInfo banServer = proxy.getServerInfo(banServerName);
                if (banServer != null) {
                    return;
                }
            }

            event.setCancelled(true);
            String message = ChatColor.RED + "" + ChatColor.BOLD + "AEGIS PUNISH - VOCÊ ESTÁ BANIDO!\n\n"
                    + ChatColor.GRAY + "ID: " + ChatColor.YELLOW + "#" + p.getId() + "\n"
                    + ChatColor.GRAY + "Motivo: " + ChatColor.WHITE + p.getReason() + "\n"
                    + ChatColor.GRAY + "Expiração: " + ChatColor.WHITE + (p.getExpiresAt() != null ? p.getExpiresAt().toString() : "Permanente") + "\n"
                    + ChatColor.GOLD + "Discord para revisão de punição.";
            event.setCancelReason(TextComponent.fromLegacyText(message));

            if (discordClient != null) {
                discordClient.sendPreventedEmbed(connection.getName(), uuid, ip, p);
            }
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        if (!banServerEnabled) return;
        UUID uuid = event.getPlayer().getUniqueId();
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();

        if (cacheManager.findActiveBan(uuid, ip, event.getPlayer().getName(), "GLOBAL").isPresent()) {
            ServerInfo banServer = proxy.getServerInfo(banServerName);
            if (banServer != null) {
                event.setTarget(banServer);
            }
        }
    }
}
