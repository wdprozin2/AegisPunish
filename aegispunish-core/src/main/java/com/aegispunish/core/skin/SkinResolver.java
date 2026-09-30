package com.aegispunish.core.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

public class SkinResolver {

    private final Logger logger;
    private boolean floodgateLoaded = false;
    private boolean skinsRestorerLoaded = false;

    public SkinResolver(Logger logger) {
        this.logger = logger;
        detectDependencies();
    }

    private void detectDependencies() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateLoaded = true;
            logger.info("[AegisPunish] Suporte a Floodgate / Geyser (Bedrock) detectado!");
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            skinsRestorerLoaded = true;
            logger.info("[AegisPunish] Suporte a SkinsRestorer detectado!");
        } catch (ClassNotFoundException ignored) {}
    }

    public String resolveAvatarUrl(UUID playerUuid, String playerName) {
        // 1. Bedrock / Floodgate player: mc-heads resolves Floodgate UUIDs directly to Bedrock skin avatars
        if (isFloodgatePlayer(playerUuid, playerName)) {
            if (playerUuid != null) {
                return "https://mc-heads.net/avatar/" + playerUuid + "/100";
            }
            String cleanName = cleanBedrockPrefix(playerName);
            return "https://mc-heads.net/avatar/" + cleanName + "/100";
        }

        // 2. SkinsRestorer (Offline Java servers / custom skin textures)
        if (skinsRestorerLoaded && playerUuid != null) {
            try {
                net.skinsrestorer.api.SkinsRestorer sr = net.skinsrestorer.api.SkinsRestorerProvider.get();
                var skinProperty = sr.getPlayerStorage().getSkinForPlayer(playerUuid, playerName);
                if (skinProperty.isPresent()) {
                    String hash = extractTextureHash(skinProperty.get().getValue());
                    if (hash != null) {
                        return "https://mc-heads.net/avatar/" + hash + "/100";
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Any valid player UUID (Java online v4, offline v3, etc.)
        if (playerUuid != null) {
            return "https://mc-heads.net/avatar/" + playerUuid + "/100";
        }

        // 4. Fallback to clean player name
        if (playerName != null && !playerName.isBlank()) {
            return "https://mc-heads.net/avatar/" + cleanBedrockPrefix(playerName) + "/100";
        }

        return "https://mc-heads.net/avatar/MHF_Steve/100";
    }

    public boolean isFloodgatePlayer(UUID playerUuid, String playerName) {
        // Floodgate UUIDs always have MSB == 0 (most significant bits)
        if (playerUuid != null && playerUuid.getMostSignificantBits() == 0L) {
            return true;
        }
        if (floodgateLoaded && playerUuid != null) {
            try {
                if (org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(playerUuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        if (playerName != null && (playerName.startsWith(".") || playerName.startsWith("*"))) {
            return true;
        }
        return false;
    }

    private String cleanBedrockPrefix(String name) {
        if (name == null) return "MHF_Steve";
        if (name.startsWith(".") || name.startsWith("*")) {
            return name.substring(1);
        }
        return name;
    }

    public String extractTextureHash(String base64Value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Value);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures != null && textures.has("SKIN")) {
                String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
                return url.substring(url.lastIndexOf("/") + 1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isFloodgateLoaded() { return floodgateLoaded; }
    public boolean isSkinsRestorerLoaded() { return skinsRestorerLoaded; }
}
