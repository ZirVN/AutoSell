/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 *
 */
package net.vdm.autosellvdm;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

// VdmTheme nằm cùng package ở bản standalone

/**
 * Bảng "Farm Stats" trên màn hình — thẻ bo góc có viền, tiêu đề căn giữa + gạch phân cách, rồi 4
 * dòng Time / Money / Items / Sells với cột giá trị thẳng hàng.
 *
 * <p>Hai điểm khác bản trước:
 * <ul>
 *   <li>dòng <b>Money</b> chỉ hiện SỐ TIỀN ĐANG CÓ, bỏ cột "$/h" bên phải (dòng Items vẫn giữ tốc
 *       độ mỗi giờ — đó mới là con số đáng liếc khi farm);</li>
 *   <li>thẻ <b>kéo thả được</b>: mở menu Auto Bán rồi nắm thẻ mà kéo; vị trí lưu vào
 *       {@code statsHudX}/{@code statsHudY} nên giữ nguyên qua lần chơi sau.</li>
 * </ul>
 */
public class AutoSellHud implements HudRenderCallback
{
    private static final int CARD_BG     = 0xE60D0E11;
    private static final int CARD_SHADOW = 0x40000000;
    private static final int CARD_BORDER = 0xFF2E3037;
    private static final int SEP         = 0x2BFFFFFF;
    private static final int HEADER      = 0xFFFFFFFF;
    private static final int LABEL       = 0xFF9BA1AB;
    private static final int VALUE       = 0xFFFFFFFF;
    private static final int RATE        = 0xFF55E06A;
    private static final int HINT        = 0xFF7A828E;
    private static final int STATE_ON    = 0xFF55E06A;
    private static final int STATE_WAIT  = 0xFFE8C55A;

    private static final int PAD   = 7;
    private static final int ROW_H = 12;
    private static final int GAP   = 14;

    /** Chữ nhắc lúc mở menu, để biết thẻ này nắm kéo được. */
    private static final String HINT_TEXT = "⇕ kéo thả để di chuyển";

    // ------------------------------------------------------------------
    // Dùng chung giữa các mod cùng vẽ thẻ này (litematica fork + AutoSellVDM)
    // ------------------------------------------------------------------
    //
    // Cài cả hai mod thì cả hai cùng vẽ "Farm Stats" ở đúng một chỗ — chữ chồng chữ, nhìn như bị
    // nhoè, mà kéo ở menu mod này thì thẻ mod kia vẫn nằm im. Ba khoá dưới đây đi qua system
    // property nên hai mod thấy nhau ngay trong cùng một tiến trình game: một mod nhận vẽ, mod kia
    // nhường; vị trí thì chung một cặp toạ độ nên kéo ở menu nào cũng dời đúng cái đang hiện.
    private static final String OWNER_KEY = "vdm.statshud.owner";
    private static final String BEAT_KEY  = "vdm.statshud.beat";
    private static final String X_KEY     = "vdm.statshud.x";
    private static final String Y_KEY     = "vdm.statshud.y";
    private static final String MOD_ID    = "autosellvdm";

    /** Nhận quyền vẽ thẻ; false = mod khác đang vẽ rồi (nhịp tim còn mới dưới 1 giây). */
    private static boolean claimHud()
    {
        long now = System.currentTimeMillis();
        String owner = System.getProperty(OWNER_KEY, "");
        long beat;

        try { beat = Long.parseLong(System.getProperty(BEAT_KEY, "0")); }
        catch (NumberFormatException e) { beat = 0L; }

        if (owner.isEmpty() == false && owner.equals(MOD_ID) == false && now - beat < 1000L)
        {
            return false;
        }

        System.setProperty(OWNER_KEY, MOD_ID);
        System.setProperty(BEAT_KEY, Long.toString(now));
        return true;
    }

    private static int sharedPos(String key)
    {
        try { return Integer.parseInt(System.getProperty(key, "")); }
        catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }

    @Override
    public void onHudRender(GuiGraphics ctx, DeltaTracker delta)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.options.hideGui)
        {
            return;
        }
        draw(ctx, mc, false);
    }

    /**
     * Bản xem trước vẽ trong menu Auto Bán: hiện thẻ kèm dòng nhắc, để còn có cái mà nắm kéo ngay
     * cả khi đang không chạy phiên bán nào.
     */
    public static void renderPreview(GuiGraphics ctx)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null)
        {
            draw(ctx, mc, true);
        }
    }

    /**
     * Khung thẻ hiện tại {x, y, rộng, cao} — menu dùng để biết chuột có nắm trúng thẻ không; null
     * khi thẻ đang không hiện.
     */
    public static int[] hudRect(Minecraft mc, boolean preview)
    {
        return draw(null, mc, preview);
    }

    /** Dời thẻ tới (x, y), kẹp trong màn hình. */
    public static void moveHud(int x, int y, int guiWidth, int guiHeight)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.statsHudX = Math.max(0, Math.min(x, Math.max(0, guiWidth - 40)));
        cfg.statsHudY = Math.max(0, Math.min(y, Math.max(0, guiHeight - 20)));
        // Đẩy lên kênh chung để thẻ của mod kia dời theo NGAY, không phải đợi khởi động lại.
        System.setProperty(X_KEY, Integer.toString(cfg.statsHudX));
        System.setProperty(Y_KEY, Integer.toString(cfg.statsHudY));
    }

    public static void saveHudPos()
    {
        AutoSellVdmConfig.get().save();
    }

    /**
     * Vẽ thẻ (hoặc chỉ ĐO khi {@code ctx} null) và trả về khung {x, y, rộng, cao}.
     *
     * <p>Đo và vẽ để chung một chỗ là có lý do: cỡ thẻ đổi theo nội dung (số tiền dài ra, dòng trạng
     * thái xuất hiện...), tách làm hai nơi là vùng nắm chuột lệch khỏi thẻ ngay khi số liệu nhảy.
     */
    private static int[] draw(GuiGraphics ctx, Minecraft mc, boolean preview)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        boolean sellOn  = cfg.autoSellEnabled;
        boolean statsOn = cfg.autoSellStats && SellStats.isTracking();

        if (preview)
        {
            statsOn = cfg.autoSellStats;
        }
        if (sellOn == false && statsOn == false)
        {
            return null; // không bật gì → không rác màn hình
        }
        // Bản xem trước trong menu thì luôn được vẽ (không thì mở menu ra chẳng có gì mà kéo);
        // còn thẻ trên HUD thì chỉ MỘT mod vẽ.
        if (ctx != null && preview == false && claimHud() == false)
        {
            return null;
        }

        Font font = mc.font;
        String title = "Farm Stats";

        String[] labels = { "Time:", "Money:", "Items:", "Sells:" };
        String[] values = { "", "", "", "" };
        String[] rates  = { "", "", "", "" };

        if (statsOn)
        {
            values[0] = SellStats.formatDuration();
            // Money: CHỈ số tiền đang có — cột "$/h" đã bỏ hẳn theo yêu cầu.
            values[1] = SellStats.formatCurrency(SellStats.getTotalEarned());
            values[2] = String.format("%,d", SellStats.getTotalItemsSold());
            rates[2]  = String.format("%,d", Math.round(itemsPerHour())) + "/h";
            values[3] = String.valueOf(SellStats.getTotalSalesCount());
        }

        String status = statusLine(sellOn);

        // ---- đo bề ngang: nhãn | giá trị | tốc độ ------------------------
        int labelW = 0;
        int valueW = 0;
        int rateW  = 0;

        if (statsOn)
        {
            for (int i = 0; i < 4; i++)
            {
                labelW = Math.max(labelW, font.width(labels[i]));
                valueW = Math.max(valueW, font.width(values[i]));
                rateW  = Math.max(rateW,  font.width(rates[i]));
            }
        }

        int bodyW = statsOn ? labelW + 6 + valueW + (rateW > 0 ? GAP + rateW : 0) : 0;
        int cardW = Math.max(Math.max(font.width(title), font.width(status)), bodyW);

        if (preview)
        {
            cardW = Math.max(cardW, font.width(HINT_TEXT));
        }
        cardW += PAD * 2;

        int rows  = statsOn ? 4 : 0;
        int cardH = PAD + 10 + 5 + rows * ROW_H + (status.isEmpty() ? 0 : ROW_H)
                + (preview ? ROW_H : 0) + PAD - 2;

        // ---- vị trí: theo config; chưa kéo bao giờ thì góc trên-trái như cũ ----
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        // Kênh chung được ưu tiên: kéo ở menu mod NÀO thì thẻ đang hiện cũng dời theo.
        int shX = sharedPos(X_KEY);
        int shY = sharedPos(Y_KEY);
        int posX = shX != Integer.MIN_VALUE ? shX : cfg.statsHudX;
        int posY = shY != Integer.MIN_VALUE ? shY : cfg.statsHudY;

        // Vị trí vừa bị kéo (có thể từ menu mod khác) → chép về config của mình để còn giữ đúng chỗ
        // sau khi khởi động lại. Chỉ ghi khi lệch, nên mỗi lượt kéo tốn đúng một lần lưu.
        if (ctx != null && shX != Integer.MIN_VALUE && (shX != cfg.statsHudX || shY != cfg.statsHudY))
        {
            cfg.statsHudX = shX;
            cfg.statsHudY = shY;
            cfg.save();
        }

        int x = posX >= 0 ? Math.min(posX, Math.max(0, screenW - cardW)) : 5;
        int y = posY >= 0 ? Math.min(posY, Math.max(0, screenH - cardH)) : 5;

        if (ctx == null)
        {
            return new int[] { x, y, cardW, cardH };
        }

        // ---- thẻ -----------------------------------------------------------
        VdmTheme.roundedRect(ctx, x + 1, y + 2, cardW, cardH, 6, CARD_SHADOW);
        VdmTheme.roundedRect(ctx, x, y, cardW, cardH, 6, CARD_BG);
        VdmTheme.roundedBorder(ctx, x, y, cardW, cardH, 6, CARD_BORDER);

        // tiêu đề căn giữa + gạch phân cách
        int ty = y + PAD;
        ctx.drawString(font, title, x + (cardW - font.width(title)) / 2, ty, HEADER, true);
        ctx.fill(x + PAD, ty + 11, x + cardW - PAD, ty + 12, SEP);

        int ry = ty + 16;

        if (statsOn)
        {
            int valueX = x + PAD + labelW + 6;
            for (int i = 0; i < 4; i++)
            {
                ctx.drawString(font, labels[i], x + PAD, ry, LABEL, true);
                ctx.drawString(font, values[i], valueX, ry, VALUE, true);

                if (rates[i].isEmpty() == false)
                {
                    ctx.drawString(font, rates[i], x + cardW - PAD - font.width(rates[i]), ry, RATE, true);
                }
                ry += ROW_H;
            }
        }

        if (status.isEmpty() == false)
        {
            AutoSell sell = AutoSell.getInstance();
            boolean waiting = sell.isWaitingForItems() || sell.isWaitingForRestart();
            ctx.drawString(font, status, x + PAD, ry, waiting ? STATE_WAIT : STATE_ON, true);
            ry += ROW_H;
        }

        if (preview)
        {
            ctx.drawString(font, HINT_TEXT, x + PAD, ry, HINT, true);
        }

        return new int[] { x, y, cardW, cardH };
    }

    /** Vật phẩm mỗi giờ — SellStats chỉ có tổng, nên chia theo thời gian chạy. */
    private static double itemsPerHour()
    {
        long ms = SellStats.getElapsedMs();
        if (ms <= 0L)
        {
            return 0.0;
        }
        return SellStats.getTotalItemsSold() * 3_600_000.0 / ms;
    }

    /** Dòng trạng thái cuối bảng: đang bán / chờ đồ / đang chờ server restart. */
    private static String statusLine(boolean sellOn)
    {
        if (sellOn == false)
        {
            return "";
        }

        AutoSell sell = AutoSell.getInstance();

        if (sell.isWaitingForRestart())
        {
            return "● Server restart — chờ " + sell.holdSecondsLeft() + "s";
        }
        if (sell.windowSecondsLeft() > 0)
        {
            return "● Đợt nhét lẻ — còn " + sell.windowSecondsLeft() + "s";
        }
        return sell.isWaitingForItems()
                ? "● Chờ đồ — nhịp " + sell.loadNumber()
                : "● Đang bán — nhịp " + sell.loadNumber();
    }
}

// © 2026 vuducmanh09 — standalone AutoSellVDM mod.
