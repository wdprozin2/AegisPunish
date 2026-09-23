package com.aegispunish.api.model;

public enum PunishmentType {
    BAN(true, false),
    TEMPBAN(true, true),
    IPBAN(true, false),
    TEMPIPBAN(true, true),
    MUTE(false, false),
    TEMPMUTE(false, true),
    WARN(false, false),
    KICK(false, false);

    private final boolean ban;
    private final boolean temporary;

    PunishmentType(boolean ban, boolean temporary) {
        this.ban = ban;
        this.temporary = temporary;
    }

    public boolean isBan() {
        return ban;
    }

    public boolean isTemporary() {
        return temporary;
    }
}
