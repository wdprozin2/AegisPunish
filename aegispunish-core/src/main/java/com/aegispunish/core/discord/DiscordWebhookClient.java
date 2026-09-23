package com.aegispunish.core.discord;

import com.aegispunish.api.model.Punishment;
import com.aegispunish.core.skin.SkinResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class DiscordWebhookClient {

    private final String webhookUrl;
    private final SkinResolver skinResolver;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Logger logger;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'às' HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public DiscordWebhookClient(String webhookUrl, SkinResolver skinResolver, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.skinResolver = skinResolver;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendPunishEmbed(Punishment p) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        executor.submit(() -> {
            try {
                String avatarUrl = skinResolver.resolveAvatarUrl(p.getTargetUuid(), p.getTargetName());

                JsonObject embed = new JsonObject();
                embed.addProperty("title", p.getTargetName() + " foi punido!");
                embed.addProperty("description", "Na proxima vez leia as regras!");
                embed.addProperty("color", 0xFF0000);

                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", avatarUrl);
                embed.add("thumbnail", thumbnail);

                JsonArray fields = new JsonArray();
                fields.add(createField("ID", "#" + p.getId(), true));
                fields.add(createField("Punição", formatPunishType(p), true));
                fields.add(createField("Motivo", p.getReason(), true));

                fields.add(createField("Servidor", p.getServerScope(), true));
                fields.add(createField("Prova", p.getProof() != null ? p.getProof() : "Nenhuma", true));
                fields.add(createField("Quem puniu", p.getPunisherName(), true));

                String durationText = formatDurationDetails(p.getCreatedAt(), p.getExpiresAt());
                fields.add(createField("Duração", durationText, false));

                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "UUID do usuário: " + (p.getTargetUuid() != null ? p.getTargetUuid() : "Desconhecido"));
                footer.addProperty("icon_url", avatarUrl);
                embed.add("footer", footer);

                sendPayload(embed);
            } catch (Exception e) {
                logger.warning("Falha ao despachar webhook de punição para o Discord: " + e.getMessage());
            }
        });
    }

    public void sendPardonEmbed(long punishId, String targetName, UUID targetUuid, String type, String reason, String revokerName) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        executor.submit(() -> {
            try {
                String avatarUrl = skinResolver.resolveAvatarUrl(targetUuid, targetName);

                JsonObject embed = new JsonObject();
                embed.addProperty("title", targetName + " foi perdoado!");
                embed.addProperty("color", 0xE91E63);

                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", avatarUrl);
                embed.add("thumbnail", thumbnail);

                JsonArray fields = new JsonArray();
                fields.add(createField("ID", "#" + punishId, true));
                fields.add(createField("Punição", type, true));
                fields.add(createField("Motivo", reason, true));
                fields.add(createField("Quem revogou", revokerName, false));

                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "UUID do usuário: " + (targetUuid != null ? targetUuid : "Desconhecido"));
                footer.addProperty("icon_url", avatarUrl);
                embed.add("footer", footer);

                sendPayload(embed);
            } catch (Exception e) {
                logger.warning("Falha ao despachar webhook de perdão para o Discord: " + e.getMessage());
            }
        });
    }

    public void sendPreventedEmbed(String targetName, UUID targetUuid, String targetIp, Punishment p) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        executor.submit(() -> {
            try {
                String avatarUrl = skinResolver.resolveAvatarUrl(targetUuid, targetName);

                JsonObject embed = new JsonObject();
                embed.addProperty("title", targetName + " foi barrado!");
                embed.addProperty("description", targetName + " tentou entrar estando banido (#" + p.getId() + ").");
                embed.addProperty("color", 0xFF0000);

                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", avatarUrl);
                embed.add("thumbnail", thumbnail);

                JsonArray fields = new JsonArray();
                fields.add(createField("Punição", formatPunishType(p), true));
                fields.add(createField("IP", targetIp, true));

                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "UUID do usuário: " + (targetUuid != null ? targetUuid : "Desconhecido"));
                footer.addProperty("icon_url", avatarUrl);
                embed.add("footer", footer);

                sendPayload(embed);
            } catch (Exception e) {
                logger.warning("Falha ao despachar webhook de tentativa barrada: " + e.getMessage());
            }
        });
    }

    private JsonObject createField(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private void sendPayload(JsonObject embed) throws Exception {
        JsonArray embedsArray = new JsonArray();
        embedsArray.add(embed);

        JsonObject root = new JsonObject();
        root.add("embeds", embedsArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }

    private String formatDurationDetails(Instant created, Instant expires) {
        String createdStr = DATE_FORMATTER.format(created);
        if (expires == null) {
            return createdStr + " Permanente";
        }
        Duration duration = Duration.between(created, expires);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%s %dh %02dmin %02ds", createdStr, hours, minutes, seconds);
    }

    private String formatPunishType(Punishment p) {
        return switch (p.getType()) {
            case BAN -> "Banimento permanente";
            case TEMPBAN -> "Banimento temporário";
            case IPBAN -> "Banimento por IP permanente";
            case TEMPIPBAN -> "Banimento por IP temporário";
            case MUTE -> "Silenciamento permanente";
            case TEMPMUTE -> "Silenciamento temporário";
            case WARN -> "Aviso / Alerta";
            case KICK -> "Expulsão";
        };
    }
}
