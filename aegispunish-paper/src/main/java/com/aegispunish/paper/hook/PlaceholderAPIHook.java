package com.aegispunish.paper.hook;

import com.aegispunish.core.manager.PunishmentManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final PunishmentManager punishmentManager;

    public PlaceholderAPIHook(PunishmentManager punishmentManager) {
        this.punishmentManager = punishmentManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "aegispunish";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AegisTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("is_mute")) {
            return String.valueOf(punishmentManager.isMuted(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("warns")) {
            return String.valueOf(punishmentManager.getWarnCount(player.getUniqueId()));
        }

        return null;
    }
}
