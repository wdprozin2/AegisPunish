package com.aegispunish.paper.listener;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.core.cache.PunishmentCacheManager;
import com.aegispunish.core.discord.DiscordWebhookClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

public class AsyncPlayerPreLoginListener implements Listener {

    private final PunishmentCacheManager cacheManager;
    private final DiscordWebhookClient discordClient;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AsyncPlayerPreLoginListener(PunishmentCacheManager cacheManager, DiscordWebhookClient discordClient) {
        this.cacheManager = cacheManager;
        this.discordClient = discordClient;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();

        cacheManager.recordIpHistory(uuid, name, ip);

        Optional<Punishment> activeBan = cacheManager.findActiveBan(uuid, ip, name, "GLOBAL");
        if (activeBan.isPresent()) {
            Punishment p = activeBan.get();
            String legacyMsg = buildLegacyKickMessage(p);

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, legacyMsg);
            try {
                event.kickMessage(buildKickScreen(p));
            } catch (Throwable ignored) {}

            if (discordClient != null) {
                discordClient.sendPreventedEmbed(name, uuid, ip, p);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;

        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "";

        Optional<Punishment> activeBan = cacheManager.findActiveBan(uuid, ip, name, "GLOBAL");
        if (activeBan.isPresent()) {
            Punishment p = activeBan.get();
            String legacyMsg = buildLegacyKickMessage(p);
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, legacyMsg);
            try {
                event.kickMessage(buildKickScreen(p));
            } catch (Throwable ignored) {}
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

    private String buildLegacyKickMessage(Punishment p) {
        String expires = p.getExpiresAt() != null 
                ? DateTimeFormatter.ISO_INSTANT.format(p.getExpiresAt()) 
                : "Permanente";
        String proof = p.getProof() != null ? p.getProof() : "Nenhuma prova anexada";

        return "§c§lAEGIS PUNISH - VOCÊ ESTÁ BANIDO!\n\n" +
                "§7ID da Punição: §e#" + p.getId() + "\n" +
                "§7Motivo: §f" + p.getReason() + "\n" +
                "§7Autor: §f" + p.getPunisherName() + "\n" +
                "§7Duração: §e" + (p.getExpiresAt() == null ? "Permanente" : "Temporário") + "\n" +
                "§7Expira em: §f" + expires + "\n" +
                "§7Prova: §b" + proof + "\n\n" +
                "§6Acha que foi um engano? Solicite revisão em nosso Discord.";
    }
}
