package com.aegispunish.paper.task;

import com.aegispunish.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;

public class StatusBroadcastTask implements Runnable {

    private final DatabaseManager databaseManager;
    private final int periodHours = 24;

    public StatusBroadcastTask(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void run() {
        CompletableFuture.supplyAsync(this::queryStats).thenAccept(stats -> {
            String[] message = new String[] {
                "",
                " &4[PUNIÇÕES DO SERVIDOR]",
                " &fEm nosso servidor, &c&l" + stats.total() + "&f punições já foram registradas.",
                " &fCerca de &c&l" + stats.bansPeriod() + "&f banimentos e &c&l" + stats.mutesPeriod() + "&f mutes em " + periodHours + " horas!",
                " &cContinue denunciando jogadores infratores para auxiliar nossa equipe.",
                ""
            };

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String line : message) {
                    player.sendMessage(line.replace("&", "§"));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
            }
        });
    }

    private PunishStats queryStats() {
        String sql = """
            SELECT 
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN type IN ('BAN', 'TEMPBAN', 'IPBAN', 'TEMPIPBAN') AND created_at >= ? THEN 1 ELSE 0 END), 0) AS bans_period,
                COALESCE(SUM(CASE WHEN type IN ('MUTE', 'TEMPMUTE') AND created_at >= ? THEN 1 ELSE 0 END), 0) AS mutes_period
            FROM punishments
        """;

        java.time.Instant threshold = java.time.Instant.now().minus(java.time.Duration.ofHours(periodHours));
        java.sql.Timestamp ts = java.sql.Timestamp.from(threshold);

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, ts);
            ps.setTimestamp(2, ts);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PunishStats(
                        rs.getLong("total"),
                        rs.getLong("bans_period"),
                        rs.getLong("mutes_period")
                    );
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AegisPunish] Erro ao carregar estatísticas do broadcast: " + e.getMessage());
        }
        return new PunishStats(0, 0, 0);
    }

    private record PunishStats(long total, long bansPeriod, long mutesPeriod) {}
}
