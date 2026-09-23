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
        if (floodgateLoaded && playerUuid != null) {
            try {
                org.geysermc.floodgate.api.FloodgateApi api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
                if (api.isFloodgatePlayer(playerUuid)) {
                    return "https://api.geysermc.org/v2/skin/" + api.getPlayer(playerUuid).getXuid();
                }
            } catch (Throwable ignored) {}
        }

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

        if (playerUuid != null && playerUuid.version() == 4) {
            return "https://mc-heads.net/avatar/" + playerUuid + "/100";
        }
        return "https://mc-heads.net/avatar/" + playerName + "/100";
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
