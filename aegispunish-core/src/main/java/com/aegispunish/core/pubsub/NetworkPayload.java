package com.aegispunish.core.pubsub;

import com.aegispunish.api.model.Punishment;

public class NetworkPayload {

    public enum Action {
        APPLY,
        REVOKE
    }

    private final Action action;
    private final Punishment punishment;
    private final Long punishmentId;

    public NetworkPayload(Action action, Punishment punishment) {
        this.action = action;
        this.punishment = punishment;
        this.punishmentId = punishment != null ? punishment.getId() : null;
    }

    public NetworkPayload(Action action, Long punishmentId) {
        this.action = action;
        this.punishment = null;
        this.punishmentId = punishmentId;
    }

    public Action getAction() { return action; }
    public Punishment getPunishment() { return punishment; }
    public Long getPunishmentId() { return punishmentId; }
}
