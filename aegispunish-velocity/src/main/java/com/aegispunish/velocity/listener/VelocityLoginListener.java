package com.aegispunish.velocity.listener;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

public class VelocityLoginListener {

    private final ProxyServer proxy;
    private final PunishmentCacheManager cacheManager;
    private final DiscordWebhookClient discordClient;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final boolean banServerEnabled;
    private final String banServerName;

    public VelocityLoginListener(ProxyServer proxy,
                                 PunishmentCacheManager cacheManager,
                                 DiscordWebhookClient discordClient,
                                 boolean banServerEnabled,
                                 String banServerName) {
        this.proxy = proxy;
        this.cacheManager = cacheManager;
        this.discordClient = discordClient;
        this.banServerEnabled = banServerEnabled;
        this.banServerName = banServerName;
    }

    @Subscribe(order = PostOrder.FIRST)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
            Optional<Punishment> activeIpBan = cacheManager.findActiveIpBan(ip);

            if (activeIpBan.isPresent()) {
                Punishment punish = activeIpBan.get();
                if (!banServerEnabled) {
                    Component kickMessage = buildKickScreen(punish);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                    if (discordClient != null) {
                        discordClient.sendPreventedEmbed(event.getUsername(), null, ip, punish);
                    }
                }
            }
        });
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return EventTask.async(() -> {
            cacheManager.recordIpHistory(uuid, player.getUsername(), ip);

            Optional<Punishment> activeBan = cacheManager.findActiveBan(uuid, ip, player.getUsername(), "GLOBAL");
            if (activeBan.isPresent()) {
                Punishment punishment = activeBan.get();

                if (player.hasPermission("aegispunish.banserver.bypass")) {
                    return;
                }

                if (banServerEnabled) {
                    Optional<RegisteredServer> targetServer = proxy.getServer(banServerName);
                    if (targetServer.isPresent()) {
                        return;
                    }
                }

                Component kickMessage = buildKickScreen(punishment);
                event.setResult(LoginEvent.ComponentResult.denied(kickMessage));
                if (discordClient != null) {
                    discordClient.sendPreventedEmbed(player.getUsername(), uuid, ip, punishment);
                }
            }
        });
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        if (!banServerEnabled) return;

        Player player = event.getPlayer();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        if (cacheManager.findActiveBan(player.getUniqueId(), ip, player.getUsername(), "GLOBAL").isPresent()) {
            if (!player.hasPermission("aegispunish.banserver.bypass")) {
                Optional<RegisteredServer> banServer = proxy.getServer(banServerName);
                banServer.ifPresent(server -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(server)));
            }
        }
    }

    private Component buildKickScreen(Punishment p) {
        String template = "<red><bold>AEGIS PUNISH - VOCÊ ESTÁ BANIDO!</bold></red>\n\n" +
                "<gray>ID da Punição: <yellow>#%id%</yellow>\n" +
                "<gray>Motivo: <white>%reason%</white>\n" +
                "<gray>Autor: <white>%punisher%</white>\n" +
                "<gray>Duração: <yellow>%duration%</yellow>\n" +
                "<gray>Expira em: <white>%expires%</white>\n" +
                "<gray>Prova: <aqua>%proof%</aqua>\n\n" +
                "<gold>Acha que foi um engano? Solicite revisão em nosso Discord.</gold>";

        String expires = p.getExpiresAt() != null 
                ? DateTimeFormatter.ISO_INSTANT.format(p.getExpiresAt()) 
                : "Permanente";
        String proof = p.getProof() != null ? p.getProof() : "Nenhuma prova anexada";

        String formatted = template
                .replace("%id%", String.valueOf(p.getId()))
                .replace("%reason%", p.getReason())
                .replace("%punisher%", p.getPunisherName())
                .replace("%duration%", p.getExpiresAt() == null ? "Permanente" : "Temporário")
                .replace("%expires%", expires)
                .replace("%proof%", proof);

        return miniMessage.deserialize(formatted);
    }
}
