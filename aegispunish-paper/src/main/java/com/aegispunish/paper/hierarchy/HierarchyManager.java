package com.aegispunish.paper.hierarchy;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HierarchyManager {

    private boolean luckPermsAvailable = false;
    private boolean enabled;
    private boolean preventEqualRank;
    private final Map<String, Integer> configuredGroupWeights = new HashMap<>();

    public HierarchyManager(FileConfiguration config) {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            this.luckPermsAvailable = true;
        } catch (ClassNotFoundException ignored) {}

        reload(config);
    }

    public void reload(FileConfiguration config) {
        this.enabled = config.getBoolean("hierarchy.enabled", true);
        this.preventEqualRank = config.getBoolean("hierarchy.prevent-equal-rank", true);

        configuredGroupWeights.clear();
        ConfigurationSection section = config.getConfigurationSection("hierarchy.groups");
        if (section != null) {
            for (String group : section.getKeys(false)) {
                configuredGroupWeights.put(group.toLowerCase(), section.getInt(group, 0));
            }
        }
    }

    public CompletableFuture<Boolean> canPunish(CommandSender punisher, String targetName, UUID targetUuid) {
        if (!enabled || !(punisher instanceof Player pSender)) {
            return CompletableFuture.completedFuture(true);
        }

        if (pSender.hasPermission("aegispunish.bypass.hierarchy")) {
            return CompletableFuture.completedFuture(true);
        }

        int punisherWeight = resolveSenderWeight(pSender);

        return resolveTargetWeight(targetName, targetUuid).thenApply(targetWeight -> {
            if (punisherWeight < targetWeight) {
                return false;
            }
            if (preventEqualRank && punisherWeight == targetWeight && targetWeight > 0) {
                return false;
            }
            return true;
        });
    }

    public int resolveSenderWeight(Player player) {
        int maxWeight = 0;

        if (luckPermsAvailable) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    maxWeight = Math.max(maxWeight, resolveLuckPermsWeight(lp, user));
                }
            } catch (Exception ignored) {}
        }

        for (var entry : configuredGroupWeights.entrySet()) {
            if (player.hasPermission("group." + entry.getKey()) || player.hasPermission("aegispunish.group." + entry.getKey())) {
                maxWeight = Math.max(maxWeight, entry.getValue());
            }
        }

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("aegispunish.weight." + i)) {
                maxWeight = Math.max(maxWeight, i);
                break;
            }
        }

        return maxWeight;
    }

    public CompletableFuture<Integer> resolveTargetWeight(String targetName, UUID targetUuid) {
        Player onlineTarget = targetName != null ? Bukkit.getPlayerExact(targetName) : null;
        if (onlineTarget != null) {
            return CompletableFuture.completedFuture(resolveSenderWeight(onlineTarget));
        }

        if (targetUuid == null && targetName != null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            if (off.hasPlayedBefore() || off.isOnline()) {
                targetUuid = off.getUniqueId();
            }
        }

        if (targetUuid == null) {
            return CompletableFuture.completedFuture(0);
        }

        final UUID finalUuid = targetUuid;

        if (luckPermsAvailable) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User cachedUser = lp.getUserManager().getUser(finalUuid);
                if (cachedUser != null) {
                    return CompletableFuture.completedFuture(resolveLuckPermsWeight(lp, cachedUser));
                }
                return lp.getUserManager().loadUser(finalUuid).thenApply(user -> {
                    if (user != null) {
                        return resolveLuckPermsWeight(lp, user);
                    }
                    return 0;
                }).exceptionally(ex -> 0);
            } catch (Exception ignored) {}
        }

        return CompletableFuture.completedFuture(0);
    }

    private int resolveLuckPermsWeight(LuckPerms lp, User user) {
        int maxWeight = 0;

        for (var node : user.getNodes()) {
            if (node instanceof PermissionNode pn) {
                String perm = pn.getPermission();
                if (perm.startsWith("aegispunish.weight.")) {
                    try {
                        int w = Integer.parseInt(perm.substring("aegispunish.weight.".length()));
                        maxWeight = Math.max(maxWeight, w);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
            if (group != null) {
                if (group.getWeight().isPresent()) {
                    maxWeight = Math.max(maxWeight, group.getWeight().getAsInt());
                }
                Integer cfgW = configuredGroupWeights.get(group.getName().toLowerCase());
                if (cfgW != null) {
                    maxWeight = Math.max(maxWeight, cfgW);
                }
            }
        }

        String primary = user.getPrimaryGroup();
        if (primary != null) {
            Group pGroup = lp.getGroupManager().getGroup(primary);
            if (pGroup != null && pGroup.getWeight().isPresent()) {
                maxWeight = Math.max(maxWeight, pGroup.getWeight().getAsInt());
            }
        }

        return maxWeight;
    }
}
