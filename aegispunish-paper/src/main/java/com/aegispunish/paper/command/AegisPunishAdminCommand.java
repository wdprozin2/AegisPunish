package com.aegispunish.paper.command;

import com.aegispunish.core.importer.LegacyPunishmentImporter;
import com.aegispunish.paper.AegisPunishPaper;
import com.aegispunish.paper.update.AutoUpdateManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AegisPunishAdminCommand implements CommandExecutor, TabCompleter {

    private final AegisPunishPaper plugin;
    private final LegacyPunishmentImporter importer;
    private final AutoUpdateManager updateManager;

    public AegisPunishAdminCommand(AegisPunishPaper plugin, LegacyPunishmentImporter importer, AutoUpdateManager updateManager) {
        this.plugin = plugin;
        this.importer = importer;
        this.updateManager = updateManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("aegispunish.admin")) {
            sender.sendMessage("§cVocê não tem permissão para este comando!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e=== AegisPunish Admin Menu ===");
            sender.sendMessage("§6/" + label + " update §7- Verifica e baixa a versão mais recente do Modrinth");
            sender.sendMessage("§6/" + label + " reload §7- Recarrega configurações");
            sender.sendMessage("§6/" + label + " import <fonte> §7- Importa dados de punição");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("update")) {
            sender.sendMessage("§a[AegisPunish] Buscando atualizações no Modrinth...");
            updateManager.checkForUpdates(sender, true);
            return true;
        }

        if (sub.equals("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage("§a[AegisPunish] Configurações recarregadas com sucesso!");
            return true;
        }

        if (sub.equals("clearvanilla") || sub.equals("purgevanilla")) {
            sender.sendMessage("§e[AegisPunish] Limpando todos os banimentos nativos do Vanilla e Essentials...");
            int cleared = 0;
            try {
                var nameBanList = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
                for (var entry : new ArrayList<>(nameBanList.getBanEntries())) {
                    nameBanList.pardon(entry.getTarget());
                    cleared++;
                }
                var ipBanList = Bukkit.getBanList(org.bukkit.BanList.Type.IP);
                for (var entry : new ArrayList<>(ipBanList.getBanEntries())) {
                    ipBanList.pardon(entry.getTarget());
                    cleared++;
                }
            } catch (Throwable e) {
                sender.sendMessage("§cErro ao limpar banlist nativa: " + e.getMessage());
            }
            sender.sendMessage("§a[AegisPunish] Limpeza concluída! " + cleared + " banimentos removidos do Vanilla/Essentials.");
            return true;
        }

        if (sub.equals("import")) {
            if (args.length < 2) {
                sender.sendMessage("§e=== AegisPunish Universal Importer ===");
                sender.sendMessage("§6/" + label + " import vanilla §7- Importa de banned-players.json e banned-ips.json");
                sender.sendMessage("§6/" + label + " import essentials §7- Importa da pasta plugins/Essentials/userdata/");
                sender.sendMessage("§6/" + label + " import litebans §7- Importa de LiteBans (SQLite ou MySQL)");
                sender.sendMessage("§6/" + label + " import advancedban §7- Importa de AdvancedBan (SQLite ou MySQL)");
                sender.sendMessage("§6/" + label + " import leafpunish §7- Importa de LeafPunish (SQLite ou MySQL)");
                return true;
            }

            String target = args[1].toLowerCase();
            File pluginsFolder = Bukkit.getPluginsFolder();

            switch (target) {
                case "vanilla" -> {
                    sender.sendMessage("§a[AegisPunish] Importando punições do Minecraft Vanilla...");
                    importer.importVanilla(Bukkit.getWorldContainer()).thenAccept(count -> {
                        sender.sendMessage("§a[AegisPunish] Sucesso! " + count + " punições do Vanilla importadas.");
                    });
                }
                case "essentials" -> {
                    sender.sendMessage("§a[AegisPunish] Importando punições do EssentialsX...");
                    importer.importEssentials(new File(pluginsFolder, "Essentials/userdata")).thenAccept(count -> {
                        sender.sendMessage("§a[AegisPunish] Sucesso! " + count + " punições do EssentialsX importadas.");
                    });
                }
                case "litebans" -> {
                    sender.sendMessage("§a[AegisPunish] Importando punições do LiteBans...");
                    importer.importLiteBans(new File(pluginsFolder, "LiteBans")).thenAccept(count -> {
                        sender.sendMessage("§a[AegisPunish] Sucesso! " + count + " punições do LiteBans importadas.");
                    });
                }
                case "advancedban" -> {
                    sender.sendMessage("§a[AegisPunish] Importando punições do AdvancedBan...");
                    importer.importAdvancedBan(new File(pluginsFolder, "AdvancedBan")).thenAccept(count -> {
                        sender.sendMessage("§a[AegisPunish] Sucesso! " + count + " punições do AdvancedBan importadas.");
                    });
                }
                case "leafpunish" -> {
                    sender.sendMessage("§a[AegisPunish] Importando punições do LeafPunish...");
                    importer.importLeafPunish(new File(pluginsFolder, "LeafPunish")).thenAccept(count -> {
                        sender.sendMessage("§a[AegisPunish] Sucesso! " + count + " punições do LeafPunish importadas.");
                    });
                }
                default -> sender.sendMessage("§cOpção desconhecida. Use: vanilla, essentials, litebans, advancedban ou leafpunish.");
            }
            return true;
        }

        sender.sendMessage("§cComando desconhecido. Use: /" + label + " [update|reload|import]");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String sub : List.of("update", "reload", "import")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    list.add(sub);
                }
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            List<String> list = new ArrayList<>();
            for (String src : List.of("vanilla", "essentials", "litebans", "advancedban", "leafpunish")) {
                if (src.startsWith(args[1].toLowerCase())) {
                    list.add(src);
                }
            }
            return list;
        }
        return List.of();
    }
}
