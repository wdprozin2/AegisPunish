package com.aegispunish.paper.menu;

import com.aegispunish.core.manager.PunishmentManager;
import com.aegispunish.core.skin.SkinResolver;
import com.aegispunish.paper.util.ItemSkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class PunishMainMenu {

    public static void open(Player player, String targetName, UUID targetUuid,
                            PunishmentManager punishmentManager, SkinResolver skinResolver) {
        Inventory inv = Bukkit.createInventory(null, 45, "§8Nova punição: §f" + targetName);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName("§7 ");
            glass.setItemMeta(gMeta);
        }
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 36; i < 45; i++) inv.setItem(i, glass);

        ItemStack head = ItemSkinUtil.createPlayerHead(targetUuid, targetName, skinResolver);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§aPunindo \"" + targetName + "\"");
            hMeta.setLore(List.of("", "§7Selecione a ação desejada", "§7para este jogador."));
            head.setItemMeta(hMeta);
        }
        inv.setItem(19, head);

        ItemStack predefined = new ItemStack(Material.COMPARATOR);
        ItemMeta pMeta = predefined.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§aPunições pré-definidas");
            pMeta.setLore(List.of(
                    "",
                    " §7Use infrações pré-configuradas",
                    " §8(Punição rápida pela escada)",
                    "",
                    "§eClique para ver as opções"
            ));
            predefined.setItemMeta(pMeta);
        }
        inv.setItem(21, predefined);

        ItemStack custom = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta cMeta = custom.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§aCriar punição customizada");
            cMeta.setLore(List.of(
                    "",
                    " §7Configurar servidor, tipo,",
                    " §7duração, motivo e prova.",
                    "",
                    "§eClique para configurar"
            ));
            custom.setItemMeta(cMeta);
        }
        inv.setItem(22, custom);

        ItemStack historic = new ItemStack(Material.BOOK);
        ItemMeta histMeta = historic.getItemMeta();
        if (histMeta != null) {
            histMeta.setDisplayName("§aHistórico de punições");
            histMeta.setLore(List.of(
                    "",
                    " §7Consultar histórico completo",
                    " §8(Punições feitas e sofridas)",
                    "",
                    "§eClique para consultar"
            ));
            historic.setItemMeta(histMeta);
        }
        inv.setItem(24, historic);

        player.openInventory(inv);
    }
}
