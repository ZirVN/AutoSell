/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Player-proximity alarm. When another (non-friend) player comes within range it beeps straight
 * through the system speakers ("tít tít tít"), fires a Discord webhook, and immediately logs out of
 * the server with "Người Chơi &lt;name&gt; Đang Ở Gần Bạn".
 *
 * <p>Ported from the Litematica fork's {@code playeralert.PlayerAlert}; reads
 * {@link AutoSellVdmConfig} instead of malilib's Configs, and shares that config's friend list and
 * Discord user id.
 */
public class PlayerAlert
{
    private static final PlayerAlert INSTANCE = new PlayerAlert();

    /** True while a player is currently in range, so the alarm fires once per arrival. */
    private boolean alerted;

    public static PlayerAlert getInstance()
    {
        return INSTANCE;
    }

    public void toggle()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.playerAlertEnabled = cfg.playerAlertEnabled == false;
        cfg.save();

        if (cfg.playerAlertEnabled == false)
        {
            this.alerted = false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
            mc.player.displayClientMessage(Component.literal(cfg.playerAlertEnabled
                    ? "§7[§6Cảnh báo§7] §aBẬT"
                    : "§7[§6Cảnh báo§7] §cTẮT"), true);
        }
    }

    public void onClientTick(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.playerAlertEnabled == false || mc.level == null || mc.player == null)
        {
            this.alerted = false;
            return;
        }

        double rangeSq = (double) cfg.playerAlertRange * cfg.playerAlertRange;

        Player nearest = null;
        double bestSq = Double.MAX_VALUE;

        for (Player p : mc.level.players())
        {
            if (p == null || p == mc.player || p.isAlive() == false)
            {
                continue;
            }
            if (cfg.isFriend(p.getName().getString()))
            {
                continue; // friends never trigger the alarm
            }
            double d = p.distanceToSqr(mc.player);
            if (d <= rangeSq && d < bestSq)
            {
                bestSq = d;
                nearest = p;
            }
        }

        if (nearest == null)
        {
            this.alerted = false;
            return;
        }

        if (this.alerted)
        {
            return;
        }

        this.alerted = true;
        String msg = "Người Chơi " + nearest.getName().getString() + " Đang Ở Gần Bạn";
        mc.player.displayClientMessage(Component.literal("§c⚠ " + msg), false);
        this.sendDiscord(msg);

        if (cfg.playerAlertSound)
        {
            this.systemBeep(); // async, plays straight to the speakers
        }
        if (cfg.playerAlertLogout)
        {
            this.logout(mc, msg);
        }
    }

    /** Three short beeps generated and sent straight to the audio device (ignores MC volume). */
    private void systemBeep()
    {
        new Thread(() ->
        {
            try
            {
                float rate = 44100.0F;
                int ms = 130;
                byte[] tone = new byte[(int) (rate * ms / 1000.0F)];
                for (int i = 0; i < tone.length; i++)
                {
                    double angle = 2.0 * Math.PI * i * 1000.0 / rate; // 1000 Hz
                    tone[i] = (byte) (Math.sin(angle) * 110.0);
                }
                AudioFormat fmt = new AudioFormat(rate, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                for (int b = 0; b < 3; b++)
                {
                    line.write(tone, 0, tone.length);
                    Thread.sleep(70);
                }
                line.drain();
                line.close();
            }
            catch (Exception e)
            {
                // fall back to the AWT beep if the audio line isn't available
                try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) { }
            }
        }).start();
    }

    private void logout(Minecraft mc, String reason)
    {
        Component msg = Component.literal(reason);
        mc.execute(() ->
        {
            if (mc.getConnection() != null)
            {
                mc.getConnection().getConnection().disconnect(msg);
            }
        });
    }

    private void sendDiscord(String message)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        String url = cfg.playerAlertWebhook;

        if (url == null || url.isEmpty())
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        String player = mc.player != null ? mc.player.getName().getString() : "?";

        new Thread(() ->
        {
            try
            {
                // Ping the configured Discord account so the alert actually notifies on phone/desktop.
                // Shares the id from the sell report settings; blank = post without a mention.
                String digits = cfg.autoSellWebhookUserId == null ? ""
                        : cfg.autoSellWebhookUserId.replaceAll("[^0-9]", "");
                String mention = digits.isEmpty() ? "" : "<@" + digits + "> ";

                String payload = String.format(
                        "{\"content\": \"%s⚠️ %s — %s\", \"allowed_mentions\": {\"parse\": [\"users\"]}}",
                        mention, escapeJson(player), escapeJson(message));

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream())
                {
                    os.write(payload.getBytes("UTF-8"));
                    os.flush();
                }
                conn.getResponseCode();
                conn.disconnect();
            }
            catch (Exception e)
            {
                // silent — an alert that can't reach Discord must never break the game thread
            }
        }).start();
    }

    private static String escapeJson(String input)
    {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
