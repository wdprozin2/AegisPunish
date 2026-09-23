package com.aegispunish.paper.util;

import com.aegispunish.core.skin.SkinResolver;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class ItemSkinUtil {

    public static ItemStack createPlayerHead(UUID uuid, String name, SkinResolver skinResolver) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        if (skinResolver != null && skinResolver.isSkinsRestorerLoaded() && uuid != null) {
            try {
                net.skinsrestorer.api.SkinsRestorer sr = net.skinsrestorer.api.SkinsRestorerProvider.get();
                var skin = sr.getPlayerStorage().getSkinForPlayer(uuid, name);
                if (skin.isPresent()) {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), name);
                    profile.setProperty(new ProfileProperty("textures", skin.get().getValue(), skin.get().getSignature()));
                    meta.setPlayerProfile(profile);
                    item.setItemMeta(meta);
                    return item;
                }
            } catch (Throwable ignored) {}
        }

        PlayerProfile profile = Bukkit.createProfile(uuid != null ? uuid : UUID.randomUUID(), name);
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
