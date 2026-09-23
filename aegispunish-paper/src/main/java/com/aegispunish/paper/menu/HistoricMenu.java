package com.aegispunish.paper.menu;

import com.aegispunish.core.database.DatabaseManager;
import com.aegispunish.core.skin.SkinResolver;
import com.aegispunish.paper.util.ItemSkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HistoricMenu {

    public static void open(Player player, String targetName, UUID targetUuid,
                            DatabaseManager db, SkinResolver skinResolver) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Histórico: §f" + targetName);

        ItemStack head = ItemSkinUtil.createPlayerHead(targetUuid, targetName, skinResolver);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§b" + targetName);
            hMeta.setLore(List.of("", "§7Histórico de infrações", "§7carregado do banco de dados."));
            head.setItemMeta(hMeta);
        }
        inv.setItem(4, head);

        CompletableFuture.runAsync(() -> {
            String sql = "SELECT * FROM punishments WHERE target_name = ? ORDER BY id DESC LIMIT 28";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetName);
                try (ResultSet rs = ps.executeQuery()) {
                    int slot = 10;
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String type = rs.getString("type");
                        String reason = rs.getString("reason");
                        String punisher = rs.getString("punisher_name");
                        String server = rs.getString("server_scope");
                        boolean active = rs.getBoolean("active");

                        ItemStack item = new ItemStack(active ? Material.REDSTONE_BLOCK : Material.GRAY_DYE);
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName((active ? "§c§l" : "§7") + type + " §f#" + id);
                            List<String> lore = new ArrayList<>();
                            lore.add("§7Status: " + (active ? "§aAtivo" : "§cInativo / Revogado"));
                            lore.add("§7Servidor: §f" + server);
                            lore.add("§7Motivo: §f" + reason);
                            lore.add("§7Staffer: §f" + punisher);
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }

                        inv.setItem(slot, item);
                        slot++;
                        if (slot == 17) slot = 19;
                        if (slot == 26) slot = 28;
                        if (slot == 35) slot = 37;
                        if (slot >= 44) break;
                    }
                }
            } catch (Exception ignored) {}
        });

        player.openInventory(inv);
    }
}
