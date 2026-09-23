package com.aegispunish.core.ladder;

import com.aegispunish.api.model.PunishmentType;
import com.aegispunish.core.util.TimeParser;

import java.time.Duration;
import java.util.*;

public class ProgressivePunishLadder {

    private final Map<String, List<Tier>> ladders = new HashMap<>();

    public void registerReason(String reasonKey, List<String> progressionStrings) {
        if (progressionStrings == null || progressionStrings.isEmpty()) return;

        List<Tier> tierList = new ArrayList<>();
        for (String raw : progressionStrings) {
            String[] parts = raw.trim().split(" ");
            String typeStr = parts[0].toUpperCase(Locale.ROOT);
            PunishmentType type = switch (typeStr) {
                case "BAN" -> parts.length > 1 ? PunishmentType.TEMPBAN : PunishmentType.BAN;
                case "IPBAN" -> parts.length > 1 ? PunishmentType.TEMPIPBAN : PunishmentType.IPBAN;
                case "MUTE" -> parts.length > 1 ? PunishmentType.TEMPMUTE : PunishmentType.MUTE;
                case "KICK" -> PunishmentType.KICK;
                default -> PunishmentType.WARN;
            };

            Duration duration = parts.length > 1 ? TimeParser.parse(parts[1]) : null;
            tierList.add(new Tier(type, duration));
        }

        ladders.put(reasonKey.toLowerCase(Locale.ROOT), tierList);
    }

    public Tier resolveTier(String reasonKey, int offenseNumber) {
        if (reasonKey == null) return null;
        List<Tier> list = ladders.get(reasonKey.toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return null;

        int index = Math.min(offenseNumber - 1, list.size() - 1);
        if (index < 0) index = 0;
        return list.get(index);
    }

    public static class Tier {
        private final PunishmentType type;
        private final Duration duration;

        public Tier(PunishmentType type, Duration duration) {
            this.type = type;
            this.duration = duration;
        }

        public PunishmentType getType() { return type; }
        public Duration getDuration() { return duration; }
    }
}
