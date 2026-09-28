/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 * Ported from the Litematica fork's autosell.AutoSellWebhook.
 */
package net.vdm.autosellvdm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Posts the {@link SellStats} numbers to a Discord webhook as a rich embed. Fully async and
 * best-effort — a failed post never blocks the game thread, but it does tell the player in chat so a
 * mistyped URL isn't silently swallowed.
 */
public class AutoSellWebhook
{
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Discord embed colours: green for the periodic report, blurple for a manual test. */
    private static final int COLOR_REPORT = 0x57F287;
    private static final int COLOR_TEST   = 0x5865F2;

    /** Post the current totals: sales count, items moved, revenue, rate and uptime. */
    public static void sendReport(int minuteMark, int stagesPerCycle)
    {
        send("Báo Cáo Bán Hàng · mốc " + minuteMark + " phút", COLOR_REPORT, false, stagesPerCycle);
    }

    /**
     * Post a one-off report tied to an event (armed / disarmed / manual test). {@code ping} adds an
     * @-mention of the configured Discord user id so a phone notification actually fires.
     */
    public static void sendEvent(String title, int color, boolean ping)
    {
        send(title, color, ping, 0);
    }

    private static void send(String title, int color, boolean ping, int stagesPerCycle)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        String url = cfg.autoSellWebhook;

        if (url == null || url.isBlank())
        {
            return;
        }

        if (isValidWebhookUrl(url) == false)
        {
            msg("§cWebhook URL sai — phải là https://discord.com/api/webhooks/...");
            return;
        }

        String name = escapeJson(cfg.autoSellWebhookName);
        String avatar = escapeJson(cfg.autoSellWebhookAvatar);
        String player = escapeJson(SellStats.playerName());
        String server = escapeJson(SellStats.serverIp());

        // BẢNG STATS kiểu thẻ "Farm Stats" trong game (user chốt "giống bảng stats
        // kiểu dạng thế"): khối monospace bốn hàng Time/Money/Items/Sells, giá trị
        // thẳng cột — thay cho sáu ô fields rời rạc của bản cũ.
        String statsBlock = escapeJson(String.format(
                "Time  :  %s%n"
                + "Money :  %s   (%s/h)%n"
                + "Items :  %,d   (%,.0f/h)%n"
                + "Sells :  %d",
                SellStats.formatDuration(),
                SellStats.formatCurrency(SellStats.getTotalEarned()),
                SellStats.formatCurrency(SellStats.getEarnedPerHour()),
                SellStats.getTotalItemsSold(), SellStats.getItemsPerHour(),
                SellStats.getTotalSalesCount()));

        StringBuilder json = new StringBuilder("{");

        if (name.isEmpty() == false)
        {
            json.append("\"username\":\"").append(name).append("\",");
        }
        // Discord rejects the whole request on a malformed avatar_url, so only send a real one.
        if (avatar.startsWith("http"))
        {
            json.append("\"avatar_url\":\"").append(avatar).append("\",");
        }

        json.append("\"content\":\"").append(ping ? mention() : "").append("\",");
        json.append("\"allowed_mentions\":{\"parse\":[\"users\"]},");
        json.append("\"embeds\":[{\"title\":\"💰  ").append(escapeJson(title)).append("\",")
            .append("\"color\":").append(color).append(",")
            // Người chơi + máy chủ gọn một dòng author kèm avatar nhỏ — khỏi tốn ô.
            .append("\"author\":{\"name\":\"").append(player).append("  ·  ").append(server).append("\",")
            .append("\"icon_url\":\"https://mc-heads.net/avatar/").append(player).append("/32.png\"},")
            .append("\"description\":\"").append(escapeJson(description()))
            .append("\\n```\\n").append(statsBlock).append("\\n```\",")
            .append("\"thumbnail\":{\"url\":\"https://mc-heads.net/avatar/").append(player).append("/100.png\"},")
            .append("\"footer\":{\"text\":\"").append(escapeJson(footer(stagesPerCycle))).append("\",")
            .append("\"icon_url\":\"https://mc-heads.net/avatar/").append(player).append("/32.png\"},")
            .append("\"timestamp\":\"").append(Instant.now()).append("\"}]}");

        sendAsync(url, json.toString());
    }

    private static String footer(int stagesPerCycle)
    {
        if (stagesPerCycle <= 0)
        {
            return "AutoSellVDM";
        }

        return "AutoSellVDM · " + stagesPerCycle + " kỳ mỗi vòng";
    }

    /** One-line summary above the fields, so the embed reads as a sentence and not just a table. */
    private static String description()
    {
        if (SellStats.getTotalItemsSold() <= 0L)
        {
            return "Chưa bán được vật phẩm nào trong phiên này.";
        }

        return "Đã bán **" + SellStats.getTotalItemsSold() + "** vật phẩm, thu về **"
                + SellStats.formatCurrency(SellStats.getTotalEarned()) + "**.";
    }

    /** {@code <@id>} for the configured Discord user id, or "" when none is set. */
    private static String mention()
    {
        String id = AutoSellVdmConfig.get().autoSellWebhookUserId;

        if (id == null)
        {
            return "";
        }

        // Accept a raw id, or a pasted <@123...> / <@!123...> mention.
        String digits = id.replaceAll("[^0-9]", "");

        return digits.isEmpty() ? "" : "<@" + digits + ">";
    }

    /** Send a report right now, and tell the player whether it landed. Used by the "Test" button. */
    public static void sendTest()
    {
        String url = AutoSellVdmConfig.get().autoSellWebhook;

        if (url == null || url.isBlank())
        {
            msg("§cChưa điền Webhook URL.");
            return;
        }

        msg("§7Đang gửi thử webhook...");
        SellStats.ensureTracking(); // so the test embed shows an uptime instead of 00:00:00
        sendEvent("Gửi Thử Báo Cáo", COLOR_TEST, true);
    }

    private static boolean isValidWebhookUrl(String url)
    {
        String lower = url.trim().toLowerCase();

        return (lower.startsWith("https://discord.com/api/webhooks/")
                || lower.startsWith("https://discordapp.com/api/webhooks/")
                || lower.startsWith("https://ptb.discord.com/api/webhooks/")
                || lower.startsWith("https://canary.discord.com/api/webhooks/"))
                && lower.length() > 40;
    }

    private static void sendAsync(String url, String json)
    {
        try
        {
            CLIENT.sendAsync(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url.trim()))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "AutoSellVDM/1.0")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            ).thenAccept(response ->
            {
                int code = response.statusCode();

                if (code == 204 || code == 200)
                {
                    msg("§aĐã gửi báo cáo lên Discord.");
                }
                else if (code == 401 || code == 403 || code == 404)
                {
                    msg("§cWebhook không tồn tại hoặc đã bị xoá (HTTP " + code + ") — tạo URL mới.");
                }
                else if (code == 429)
                {
                    msg("§eDiscord chặn vì gửi quá nhanh — tăng §fMốc báo cáo§e lên.");
                }
                else
                {
                    msg("§cDiscord trả lỗi HTTP " + code + ": " + shorten(response.body()));
                }
            }).exceptionally(t ->
            {
                msg("§cKhông gửi được webhook: " + t.getMessage());
                return null;
            });
        }
        catch (Exception e)
        {
            msg("§cWebhook URL không hợp lệ: " + e.getMessage());
        }
    }

    private static String shorten(String body)
    {
        if (body == null || body.isEmpty())
        {
            return "(không có nội dung)";
        }

        return body.length() > 120 ? body.substring(0, 120) + "…" : body;
    }

    /** Chat feedback about the webhook. Scheduled onto the client thread — HTTP callbacks are async. */
    private static void msg(String text)
    {
        Minecraft mc = Minecraft.getInstance();

        mc.execute(() ->
        {
            if (mc.player != null)
            {
                mc.player.displayClientMessage(Component.literal("§7[§6AutoSell§7] §f" + text), false);
            }
        });
    }

    private static String escapeJson(String input)
    {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
