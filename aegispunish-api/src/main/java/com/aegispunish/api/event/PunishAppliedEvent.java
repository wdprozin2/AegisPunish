package com.aegispunish.api.event;

import com.aegispunish.api.model.Punishment;

public class PunishAppliedEvent {

    private final Punishment punishment;
    private final boolean silent;

    public PunishAppliedEvent(Punishment punishment, boolean silent) {
        this.punishment = punishment;
        this.silent = silent;
    }

    public Punishment getPunishment() { return punishment; }
    public boolean isSilent() { return silent; }
}
