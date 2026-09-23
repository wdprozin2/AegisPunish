package com.aegispunish.paper.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

public class LuckPermsHook {

    private boolean available = false;

    public LuckPermsHook() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            available = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public boolean canPunish(Player punisher, Player target) {
        if (!available || punisher == null || target == null) return true;
        if (punisher.hasPermission("aegispunish.bypass.hierarchy")) return true;

        try {
            LuckPerms lp = LuckPermsProvider.get();
            User pUser = lp.getUserManager().getUser(punisher.getUniqueId());
            User tUser = lp.getUserManager().getUser(target.getUniqueId());

            if (pUser == null || tUser == null) return true;

            int pWeight = getPrimaryGroupWeight(lp, pUser);
            int tWeight = getPrimaryGroupWeight(lp, tUser);

            return pWeight > tWeight;
        } catch (Exception e) {
            return true;
        }
    }

    private int getPrimaryGroupWeight(LuckPerms lp, User user) {
        String groupName = user.getPrimaryGroup();
        var group = lp.getGroupManager().getGroup(groupName);
        if (group != null && group.getWeight().isPresent()) {
            return group.getWeight().getAsInt();
        }
        return 0;
    }
}
