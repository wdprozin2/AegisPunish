package com.aegispunish.paper.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class AutoUpdateManager implements Listener {

    private final Plugin plugin;
    private final String currentVersion;
    private final Logger logger;

    private boolean checkOnStartup;
    private boolean autoDownload;
    private boolean notifyAdminsOnJoin;

    private boolean updateAvailable = false;
    private boolean updateDownloaded = false;
    private String latestVersion = null;

    public AutoUpdateManager(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.logger = plugin.getLogger();

        reloadConfig(config);

        if (checkOnStartup) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                checkForUpdates(null, autoDownload);
            }, 60L);
        }
    }

    public void reloadConfig(FileConfiguration config) {
        this.checkOnStartup = config.getBoolean("updates.check-on-startup", true);
        this.autoDownload = config.getBoolean("updates.auto-download", true);
        this.notifyAdminsOnJoin = config.getBoolean("updates.notify-admins-on-join", true);
    }

    public CompletableFuture<Boolean> checkForUpdates(CommandSender sender, boolean downloadIfFound) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] endpoints = new String[]{
                        "https://api.modrinth.com/v2/project/hMntK9LJ/version",
                        "https://api.modrinth.com/v2/project/aegispunish/version"
                };

                HttpURLConnection conn = null;
                for (String endpoint : endpoints) {
                    try {
                        URI uri = URI.create(endpoint);
                        HttpURLConnection candidate = (HttpURLConnection) uri.toURL().openConnection();
                        candidate.setRequestMethod("GET");
                        candidate.setRequestProperty("User-Agent", "AegisPunish-AutoUpdater/1.0.3");
                        candidate.setConnectTimeout(8000);
                        candidate.setReadTimeout(8000);
                        if (candidate.getResponseCode() == 200) {
                            conn = candidate;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (conn == null) {
                    URI uri = URI.create("https://api.modrinth.com/v2/project/hMntK9LJ/version");
                    HttpURLConnection candidate = (HttpURLConnection) uri.toURL().openConnection();
                    candidate.setRequestMethod("GET");
                    candidate.setRequestProperty("User-Agent", "AegisPunish-AutoUpdater/1.0.3");
                    candidate.setRequestProperty("Authorization", "mrp_wjhCpyaJyPCi7SSFPgwqipR7WtmOEvrZrjTU4WHxh9Q5tMC2NpQNGmvoI6kS");
                    candidate.setConnectTimeout(8000);
                    candidate.setReadTimeout(8000);
                    if (candidate.getResponseCode() == 200) {
                        conn = candidate;
                    }
                }

                if (conn == null || conn.getResponseCode() != 200) {
                    if (sender != null) {
                        int code = conn != null ? conn.getResponseCode() : 0;
                        sender.sendMessage("§c[AegisPunish] Erro ao consultar atualizações no Modrinth: HTTP " + code);
                    }
                    return false;
                }

                String responseBody;
                try (InputStream in = conn.getInputStream()) {
                    responseBody = new String(in.readAllBytes());
                }

                JsonArray versions = JsonParser.parseString(responseBody).getAsJsonArray();
                for (JsonElement el : versions) {
                    JsonObject vObj = el.getAsJsonObject();
                    JsonArray loaders = vObj.getAsJsonArray("loaders");
                    boolean matchesLoader = false;
                    for (JsonElement l : loaders) {
                        String loaderStr = l.getAsString().toLowerCase();
                        if (loaderStr.equals("paper") || loaderStr.equals("purpur") || loaderStr.equals("folia") || loaderStr.equals("spigot")) {
                            matchesLoader = true;
                            break;
                        }
                    }

                    if (!matchesLoader) continue;

                    String vNumber = vObj.get("version_number").getAsString();
                    if (isNewer(vNumber, currentVersion)) {
                        this.updateAvailable = true;
                        this.latestVersion = vNumber;

                        logger.info("[AegisPunish] Nova versão encontrada no Modrinth: v" + vNumber + " (versão instalada: v" + currentVersion + ")");
                        if (sender != null) {
                            sender.sendMessage("§a[AegisPunish] Nova versão encontrada: §ev" + vNumber + " §a(atual: §7v" + currentVersion + "§a)!");
                        }

                        if (downloadIfFound) {
                            JsonArray files = vObj.getAsJsonArray("files");
                            String downloadUrl = null;
                            String filename = "AegisPunish-Paper.jar";

                            for (JsonElement fEl : files) {
                                JsonObject fObj = fEl.getAsJsonObject();
                                String fn = fObj.get("filename").getAsString();
                                if (fn.toLowerCase().contains("paper")) {
                                    downloadUrl = fObj.get("url").getAsString();
                                    filename = fn;
                                    break;
                                }
                            }

                            if (downloadUrl == null && files.size() > 0) {
                                JsonObject fObj = files.get(0).getAsJsonObject();
                                downloadUrl = fObj.get("url").getAsString();
                                filename = fObj.get("filename").getAsString();
                            }

                            if (downloadUrl != null) {
                                boolean ok = downloadUpdate(downloadUrl, filename);
                                if (ok) {
                                    this.updateDownloaded = true;
                                    logger.info("[AegisPunish] Atualização v" + vNumber + " baixada com sucesso! Será aplicada na próxima reinicialização.");
                                    if (sender != null) {
                                        sender.sendMessage("§a[AegisPunish] Atualização §ev" + vNumber + " §abaixada com sucesso! Ela será ativada no próximo reinício do servidor.");
                                    }
                                }
                            }
                        }
                        return true;
                    } else {
                        break;
                    }
                }

                if (sender != null) {
                    sender.sendMessage("§a[AegisPunish] Seu plugin já está na versão mais recente (v" + currentVersion + ").");
                }
                return false;
            } catch (Exception e) {
                logger.warning("[AegisPunish] Falha ao verificar atualizações: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage("§c[AegisPunish] Falha ao verificar atualizações: " + e.getMessage());
                }
                return false;
            }
        });
    }

    private boolean downloadUpdate(String fileUrl, String filename) {
        try {
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File targetFile = new File(updateFolder, filename);

            URI uri = URI.create(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AegisPunish-AutoUpdater/1.0.3");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return true;
        } catch (Exception e) {
            logger.warning("[AegisPunish] Erro ao baixar atualização: " + e.getMessage());
            return false;
        }
    }

    private boolean isNewer(String remote, String local) {
        String[] r = remote.replace("v", "").split("\\.");
        String[] l = local.replace("v", "").split("\\.");

        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rNum = i < r.length ? parseIntSafe(r[i]) : 0;
            int lNum = i < l.length ? parseIntSafe(l[i]) : 0;
            if (rNum > lNum) return true;
            if (rNum < lNum) return false;
        }
        return false;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!notifyAdminsOnJoin) return;
        Player player = event.getPlayer();
        if (player.hasPermission("aegispunish.admin")) {
            if (updateDownloaded) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage("§6§l[AegisPunish] §aUma nova atualização (§ev" + latestVersion + "§a) foi baixada e será aplicada no próximo reinício!");
                }, 40L);
            } else if (updateAvailable) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage("§6§l[AegisPunish] §eNova versão disponível no Modrinth: §av" + latestVersion + "§e! Digite §f/aegis update §eou §f/aegispunish update §epara atualizar.");
                }, 40L);
            }
        }
    }

    public boolean isUpdateDownloaded() {
        return updateDownloaded;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
