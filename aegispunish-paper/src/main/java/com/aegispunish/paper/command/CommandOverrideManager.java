package com.aegispunish.paper.command;

import com.aegispunish.core.manager.PunishmentManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

public class CommandOverrideManager implements Listener {

    private final PunishmentManager punishmentManager;
    private final Logger logger;

    private static final Set<String> OVERRIDDEN_COMMANDS = Set.of(
            "ban", "tempban", "ipban", "tempipban",
            "mute", "tempmute", "unban", "pardon",
            "unmute", "kick", "warn"
    );

    public CommandOverrideManager(PunishmentManager punishmentManager, Logger logger) {
        this.punishmentManager = punishmentManager;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public void hijackCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for (String cmdName : OVERRIDDEN_COMMANDS) {
                knownCommands.remove("minecraft:" + cmdName);
                knownCommands.remove("essentials:" + cmdName);
                knownCommands.remove("ess:" + cmdName);
            }
            logger.info("[AegisPunish] Injeção no CommandMap realizada com sucesso!");
        } catch (Exception e) {
            logger.warning("[AegisPunish] Aviso ao injetar no CommandMap: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (handleInterceptedCommand(message, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String message = "/" + event.getCommand();
        if (handleInterceptedCommand(message, event.getSender())) {
            event.setCancelled(true);
        }
    }

    private boolean handleInterceptedCommand(String rawMessage, Object sender) {
        String[] parts = rawMessage.substring(1).split(" ");
        String label = parts[0].toLowerCase(Locale.ROOT);

        if (label.contains(":")) {
            String prefix = label.substring(0, label.indexOf(":"));
            String command = label.substring(label.indexOf(":") + 1);

            if ((prefix.equals("minecraft") || prefix.equals("essentials") || prefix.equals("ess"))
                    && OVERRIDDEN_COMMANDS.contains(command)) {
                String newCommand = rawMessage.replaceFirst("/" + prefix + ":", "/");
                if (sender instanceof Player player) {
                    player.chat(newCommand);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), newCommand.substring(1));
                }
                return true;
            }
        }
        return false;
    }
}
