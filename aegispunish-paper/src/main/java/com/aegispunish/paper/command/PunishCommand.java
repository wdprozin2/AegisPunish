package com.aegispunish.paper.command;

import com.aegispunish.api.model.PunishmentType;
import com.aegispunish.core.manager.PunishmentManager;
import com.aegispunish.core.skin.SkinResolver;
import com.aegispunish.core.util.TimeParser;
import com.aegispunish.paper.hierarchy.HierarchyManager;
import com.aegispunish.paper.menu.HistoricMenu;
import com.aegispunish.paper.menu.PunishMainMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final PunishmentManager punishmentManager;
    private final SkinResolver skinResolver;
    private final HierarchyManager hierarchyManager;

    public PunishCommand(PunishmentManager punishmentManager, SkinResolver skinResolver, HierarchyManager hierarchyManager) {
        this.punishmentManager = punishmentManager;
        this.skinResolver = skinResolver;
        this.hierarchyManager = hierarchyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (args.length == 0) {
            sender.sendMessage("§cUso: /" + label + " <jogador> [motivo/tempo]");
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            targetPlayer = Bukkit.getPlayer(targetName);
        }
        UUID targetUuid = targetPlayer != null ? targetPlayer.getUniqueId() : null;
        String targetIp = targetPlayer != null && targetPlayer.getAddress() != null 
                ? targetPlayer.getAddress().getAddress().getHostAddress() : null;

        if (targetPlayer != null) {
            targetName = targetPlayer.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            if (off.hasPlayedBefore() || off.isOnline()) {
                targetUuid = off.getUniqueId();
                if (off.getName() != null) targetName = off.getName();
            }
        }

        UUID punisherUuid = sender instanceof Player pSender ? pSender.getUniqueId() : UUID.nameUUIDFromBytes("Console".getBytes());
        String punisherName = sender.getName();
        boolean proofBypass = sender.hasPermission("aegispunish.proofbypass");

        if (cmd.equals("punir")) {
            if (sender instanceof Player pSender) {
                PunishMainMenu.open(pSender, targetName, targetUuid, punishmentManager, skinResolver);
                return true;
            }
            sender.sendMessage("§cO menu só pode ser aberto por jogadores!");
            return true;
        }

        if (cmd.equals("historic")) {
            if (sender instanceof Player pSender) {
                HistoricMenu.open(pSender, targetName, targetUuid, punishmentManager.getDatabaseManager(), skinResolver);
                return true;
            }
            sender.sendMessage("§cO menu só pode ser aberto por jogadores!");
            return true;
        }

        if (cmd.equals("unban") || cmd.equals("unmute") || cmd.equals("pardon")) {
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Revogado via comando";
            sender.sendMessage("§aProcessando revogação...");

            if (targetName.startsWith("#")) {
                try {
                    long id = Long.parseLong(targetName.substring(1));
                    punishmentManager.revokePunishment(id, punisherUuid, punisherName, reason).thenAccept(ok -> {
                        sender.sendMessage(ok ? "§aPunição #" + id + " revogada com sucesso!" : "§cPunição não encontrada ou já inativa.");
                    });
                    return true;
                } catch (NumberFormatException ignored) {}
            }

            boolean isBan = !cmd.equals("unmute");
            final String finalTarget = targetName;

            if (isBan) {
                try {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(finalTarget);
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(finalTarget);
                } catch (Throwable ignored) {}
            }

            punishmentManager.revokeByTarget(targetName, isBan, punisherUuid, punisherName, reason).thenAccept(ok -> {
                if (ok) {
                    sender.sendMessage("§aPunição de " + finalTarget + " revogada com sucesso!");
                } else {
                    sender.sendMessage("§aPunição revogada para " + finalTarget + ".");
                }
            }).exceptionally(ex -> {
                sender.sendMessage("§cErro ao revogar punição: " + ex.getMessage());
                return null;
            });
            return true;
        }

        final String resolvedTargetName = targetName;
        final UUID resolvedTargetUuid = targetUuid;
        final String resolvedTargetIp = targetIp;
        final Player resolvedTargetPlayer = targetPlayer;

        hierarchyManager.canPunish(sender, resolvedTargetName, resolvedTargetUuid).thenAccept(canPunish -> {
            if (!canPunish) {
                sender.sendMessage("§cVocê não pode punir este jogador devido à hierarquia de cargos!");
                return;
            }

            PunishmentType type = switch (cmd) {
                case "tempban" -> PunishmentType.TEMPBAN;
                case "ipban" -> PunishmentType.IPBAN;
                case "tempipban" -> PunishmentType.TEMPIPBAN;
                case "mute" -> PunishmentType.MUTE;
                case "tempmute" -> PunishmentType.TEMPMUTE;
                case "warn" -> PunishmentType.WARN;
                case "kick" -> PunishmentType.KICK;
                default -> PunishmentType.BAN;
            };

            Long durationMillis = null;
            int reasonStartIndex = 1;

            if (type.isTemporary()) {
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /" + label + " <jogador> <tempo> [motivo]");
                    return;
                }
                Duration duration = TimeParser.parse(args[1]);
                if (duration != null) {
                    durationMillis = duration.toMillis();
                    reasonStartIndex = 2;
                }
            }

            String reason = reasonStartIndex < args.length
                    ? String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length))
                    : "Sem motivo informado";

            boolean silent = reason.endsWith("-s");
            if (silent) {
                reason = reason.substring(0, reason.length() - 2).trim();
            }

            sender.sendMessage("§aAplicando punição...");
            punishmentManager.applyPunishment(
                    resolvedTargetUuid, resolvedTargetName, resolvedTargetIp,
                    punisherUuid, punisherName, type,
                    reason, null, "GLOBAL", durationMillis, proofBypass
            ).thenAccept(p -> {
                sender.sendMessage("§a" + resolvedTargetName + " foi punido com sucesso! (#" + p.getId() + ")");
                if (resolvedTargetPlayer != null && type.isBan()) {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AegisPunish"), () -> {
                        resolvedTargetPlayer.kickPlayer("§c§lAEGIS PUNISH - VOCÊ ESTÁ BANIDO!\n\n§7Motivo: §f" + p.getReason() + "\n§7ID: §e#" + p.getId());
                    });
                }
            }).exceptionally(ex -> {
                sender.sendMessage("§cErro ao punir: " + ex.getMessage());
                return null;
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        if (args.length == 2 && (command.getName().contains("temp"))) {
            return List.of("1h", "1d", "7d", "30d");
        }
        return Collections.emptyList();
    }
}
