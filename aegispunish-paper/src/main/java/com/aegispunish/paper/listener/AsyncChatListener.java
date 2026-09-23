package com.aegispunish.paper.listener;

import com.aegispunish.core.manager.PunishmentManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

public class AsyncChatListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final List<String> blockedMuteCommands = List.of(
            "tell", "r", "g", "l", "w", "msg", "me", "chat", "say"
    );

    public AsyncChatListener(PunishmentManager punishmentManager) {
        this.punishmentManager = punishmentManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (punishmentManager.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cVocê está silenciado e não pode falar no chat!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!punishmentManager.isMuted(player.getUniqueId())) return;

        String raw = event.getMessage().substring(1).trim().toLowerCase(Locale.ROOT);
        String command = raw.split(" ")[0];

        if (command.contains(":")) {
            command = command.substring(command.indexOf(":") + 1);
        }

        if (blockedMuteCommands.contains(command)) {
            event.setCancelled(true);
            player.sendMessage("§cVocê não pode utilizar comandos de comunicação enquanto estiver silenciado!");
        }
    }
}
