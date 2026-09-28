/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 * Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;



/**
 * Ba tính năng "né staff" dùng chung một danh sách tên:
 *
 * <ul>
 *   <li><b>Staff List HUD</b> — quét tab-list mỗi tick, staff nào online thì hiện tên trên HUD.</li>
 *   <li><b>Auto Sign</b> — staff online: ĐÓNG BĂNG automation (AutoSell... check {@link #isFrozen()}),
 *       đứng im, rút bảng trong người đặt xuống và tự ghi câu chữ cấu hình sẵn cho staff đọc.
 *       Staff off hết thì tự chạy lại.</li>
 *   <li><b>No-Render</b> — {@link #shouldHideItem} cho mixin ẩn vật phẩm rơi (ẩn hết / theo danh sách)
 *       để giảm lag.</li>
 * </ul>
 */
public final class StaffGuard
{
    private StaffGuard() {}

    private static final List<String> ONLINE = new ArrayList<>();
    /** Tên HIỂN THỊ trên tab-list của từng staff online — server tự kèm tag rank màu (SR MOD...). */
    private static final List<net.minecraft.network.chat.Component> ONLINE_DISPLAY = new ArrayList<>();
    /** Staff đang ĐỨNG GẦN (trong bán kính) — chỉ danh sách này mới kích hoạt Auto Sign đóng băng. */
    private static final List<String> NEARBY = new ArrayList<>();
    /** Đã đóng băng rồi thì staff phải đi xa hơn bán kính + ngần này block mới nhả (chống nhấp nháy). */
    private static final float HYSTERESIS = 2.0f;
    private static boolean frozen;
    private static boolean signDone;
    private static BlockPos signPos;
    private static int placeCooldown;
    private static boolean stoppedOnce;

    public static boolean isFrozen()
    {
        return frozen;
    }

    public static List<String> onlineStaff()
    {
        return ONLINE;
    }

    /** Mixin hỏi từng ItemEntity: có ẩn không? */
    public static boolean shouldHideItem(ItemEntity entity)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.noRenderEnabled == false)
        {
            return false;
        }
        return true; // No-Render gộp: bật là ẩn TẤT CẢ đồ rơi
    }

    public static void tick(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (mc.player == null || mc.level == null || mc.getConnection() == null
                || (cfg.staffHudEnabled == false && cfg.autoSignEnabled == false))
        {
            frozen = false;
            signDone = false;
            stoppedOnce = false;
            ONLINE.clear();
            ONLINE_DISPLAY.clear();
            NEARBY.clear();
            return;
        }

        ONLINE.clear();
        ONLINE_DISPLAY.clear();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers())
        {
            String name = info.getProfile().name(); // authlib mới: GameProfile là record — name(), không getName()
            for (String staff : cfg.staffNames)
            {
                if (staff != null && staff.equalsIgnoreCase(name))
                {
                    ONLINE.add(name);
                    ONLINE_DISPLAY.add(info.getTabListDisplayName() != null
                            ? info.getTabListDisplayName()
                            : net.minecraft.network.chat.Component.literal(name));
                    break;
                }
            }
        }

        if (cfg.autoSignEnabled == false)
        {
            frozen = false;
            signDone = false;
            stoppedOnce = false;
            NEARBY.clear();
            return;
        }

        // Auto Sign CHỈ kích hoạt khi có staff ĐỨNG GẦN (trong bán kính) — không phải cứ staff online
        // trong server. Quét mọi người chơi trong tầm nhìn, khớp tên staff + khoảng cách ≤ staffRadius.
        NEARBY.clear();
        int radius = Math.max(1, cfg.staffRadius);
        float limit = frozen ? radius + HYSTERESIS : radius;
        for (AbstractClientPlayer p : mc.level.players())
        {
            if (p == mc.player)
            {
                continue;
            }
            String pname = p.getGameProfile().name();
            for (String staff : cfg.staffNames)
            {
                if (staff != null && staff.equalsIgnoreCase(pname)
                        && mc.player.distanceTo(p) <= limit)
                {
                    NEARBY.add(pname);
                    break;
                }
            }
        }

        if (NEARBY.isEmpty())
        {
            // Không staff nào lại gần → mở băng, lần tới có staff gần thì đặt bảng mới.
            frozen = false;
            signDone = false;
            stoppedOnce = false;
            signPos = null;
            return;
        }

        // Có staff đứng gần: đóng băng automation (AutoSell... tự kiểm tra isFrozen) và đặt bảng 1 lần.
        frozen = true;

        if (stoppedOnce == false)
        {
            stoppedOnce = true;
            // Báo RÕ vì sao máy bán đứng — không thì user tưởng "ô bật auto sell không hoạt động".
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§e⏸ Staff §f" + String.join(", ", NEARBY) + "§e vào gần (≤" + radius
                    + " block) — Auto Sign TẠM DỪNG bán, đứng im. Tắt §fAuto Sign§e nếu muốn bán tiếp."),
                    false);
            // Đóng shop đang mở để đứng im sạch sẽ; máy bán đã bị isFrozen chặn nên không mở lại.
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)
            {
                mc.player.closeContainer();
            }
        }

        if (signDone == false)
        {
            tickSign(mc);
        }
    }

    private static void tickSign(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        // Màn hình sửa bảng đã mở → điền chữ qua packet rồi đóng — không gõ tay từng ô.
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)
        {
            if (signPos != null)
            {
                String[] lines = signLines(cfg.signText);
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
                        signPos, true, lines[0], lines[1], lines[2], lines[3]));
            }
            mc.setScreen(null);
            signDone = true;
            return;
        }

        if (mc.screen != null || mc.gameMode == null)
        {
            return;
        }
        if (placeCooldown > 0)
        {
            placeCooldown--;
            return;
        }
        placeCooldown = 10; // thử lại mỗi nửa giây, không spam click

        // Rút bảng: hotbar trước, không có thì kéo từ ba lô xuống ô đang cầm.
        var inv = mc.player.getInventory();
        int signSlot = -1;
        for (int i = 0; i < 9; i++)
        {
            if (inv.getItem(i).getItem() instanceof SignItem)
            {
                signSlot = i;
                break;
            }
        }
        if (signSlot >= 0)
        {
            inv.setSelectedSlot(signSlot);
        }
        else
        {
            for (int i = 9; i < 36; i++)
            {
                if (inv.getItem(i).getItem() instanceof SignItem)
                {
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                            i, inv.getSelectedSlot(), ClickType.SWAP, mc.player);
                    return; // tick sau kiểm tra lại (swap cần settle)
                }
            }
            return; // trong người không có bảng — thôi, chỉ đứng im
        }

        if (mc.player.getMainHandItem().getItem() instanceof SignItem == false)
        {
            return;
        }

        // Tìm ô trống cạnh chân có nền đặc để cắm bảng đứng.
        BlockPos feet = mc.player.blockPosition();
        Direction[] dirs = { mc.player.getDirection(), mc.player.getDirection().getClockWise(),
                mc.player.getDirection().getCounterClockWise(), mc.player.getDirection().getOpposite() };

        for (Direction d : dirs)
        {
            BlockPos t = feet.relative(d);
            if (mc.level.getBlockState(t).canBeReplaced()
                    && mc.level.getBlockState(t.below()).canBeReplaced() == false)
            {
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(t.below()).add(0, 0.5, 0), Direction.UP, t.below(), false);
                signPos = t;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return; // chờ SignEditScreen mở ở tick sau
            }
        }
    }

    /** Tách câu chữ cấu hình ("dòng 1|dòng 2|...") thành đúng 4 dòng bảng, mỗi dòng tối đa 15 ký tự. */
    private static String[] signLines(String text)
    {
        String[] out = { "", "", "", "" };
        if (text == null)
        {
            return out;
        }
        String[] parts = text.split("\\|");
        for (int i = 0; i < 4 && i < parts.length; i++)
        {
            String s = parts[i].trim();
            out[i] = s.length() > 15 ? s.substring(0, 15) : s;
        }
        return out;
    }

    private static final String HUD_HEAD = "👤 Online staffs :";
    private static final String HUD_HINT = "⇕ kéo thả để di chuyển";

    private static String signLine()
    {
        return signDone ? "✔ Đã đặt bảng — đứng im" : "⏳ Đang đặt bảng...";
    }

    /** Nội dung dòng: staff online thật, hoặc một dòng mẫu cho bản xem trước trong menu. */
    private static List<net.minecraft.network.chat.Component> hudLines(boolean preview)
    {
        if (ONLINE_DISPLAY.isEmpty() == false)
        {
            return ONLINE_DISPLAY;
        }
        return preview
                ? List.of(net.minecraft.network.chat.Component.literal("§2SR MOD §fShowered"))
                : List.of();
    }

    /**
     * Khung thẻ HUD hiện tại {x, y, w, h}. Vị trí lấy từ config (kéo thả trong menu);
     * -1 = tự neo góc phải-trên như cũ.
     */
    public static int[] hudRect(Minecraft mc, int guiWidth, boolean preview)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        var font = mc.font;
        List<net.minecraft.network.chat.Component> lines = hudLines(preview);

        int w = font.width(HUD_HEAD);
        for (net.minecraft.network.chat.Component c : lines)
        {
            w = Math.max(w, font.width(c));
        }
        if (cfg.autoSignEnabled)
        {
            w = Math.max(w, font.width(signLine()));
        }
        if (preview)
        {
            w = Math.max(w, font.width(HUD_HINT));
        }

        w = Math.max(w, font.width(modLine()));

        int cardW = w + 16;
        int cardH = 16 + lines.size() * 11 + (cfg.autoSignEnabled ? 11 : 0) + 11 + (preview ? 11 : 0);
        // Vị trí ưu tiên bản DÙNG CHUNG 4 mod (system property) — kéo ở menu mod nào cũng dời đúng
        // khung đang hiện; chưa có bản chung thì lấy config của mình.
        int posX = sharedPos(HUD_X_KEY) != Integer.MIN_VALUE ? sharedPos(HUD_X_KEY) : cfg.staffHudX;
        int posY = sharedPos(HUD_Y_KEY) != Integer.MIN_VALUE ? sharedPos(HUD_Y_KEY) : cfg.staffHudY;
        int x = posX >= 0 ? Math.min(posX, Math.max(0, guiWidth - cardW)) : guiWidth - cardW - 6;
        int y = Math.max(0, posY >= 0 ? posY : 5);

        return new int[] { x, y, cardW, cardH };
    }

    /** Dòng cuối thẻ: HUD đang chạy từ mod nào — user yêu cầu "thêm chữ ở cuối là mod: ...". */
    private static String modLine()
    {
        return "mod: " + HUD_MOD_ID;
    }

    private static final String HUD_X_KEY = "vdm.staffhud.x";
    private static final String HUD_Y_KEY = "vdm.staffhud.y";

    private static int sharedPos(String key)
    {
        try { return Integer.parseInt(System.getProperty(key, "")); }
        catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }

    /** Kéo thẻ tới (không ghi file mỗi tick — {@link #saveHudPos()} lúc thả chuột). */
    public static void moveHud(int x, int y, int guiWidth, int guiHeight)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.staffHudX = Math.max(0, Math.min(x, guiWidth - 40));
        cfg.staffHudY = Math.max(0, Math.min(y, guiHeight - 20));
        // Đẩy lên kênh chung để khung ngoài game (mod nào vẽ cũng vậy) dời theo NGAY.
        System.setProperty(HUD_X_KEY, Integer.toString(cfg.staffHudX));
        System.setProperty(HUD_Y_KEY, Integer.toString(cfg.staffHudY));
    }

    public static void saveHudPos()
    {
        AutoSellVdmConfig.get().save();
    }

    /**
     * HUD theo đúng mẫu user chốt: thẻ bo góc viền xám, tiêu đề "👤 Online staffs :", mỗi dòng là
     * TÊN TAB-LIST của staff — server tự kèm tag rank màu (SR MOD xanh, ADMIN đỏ...).
     */
    public static void renderHud(GuiGraphics g)
    {
        Minecraft mc = Minecraft.getInstance();
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (mc.player == null || mc.options.hideGui || cfg.staffHudEnabled == false || ONLINE_DISPLAY.isEmpty())
        {
            releaseHud();
            return;
        }
        if (claimHud() == false)
        {
            return; // mod khác đang vẽ thẻ này rồi — khỏi chồng thêm một bản
        }

        // Vị trí chung vừa bị kéo (có thể từ menu mod KHÁC) → chép về config của mình để còn giữ
        // đúng chỗ đó qua lần restart sau. Chỉ ghi khi lệch nên mỗi lượt kéo tốn đúng một lần save.
        int shX = sharedPos(HUD_X_KEY);
        int shY = sharedPos(HUD_Y_KEY);
        if (shX != Integer.MIN_VALUE && (shX != cfg.staffHudX || shY != cfg.staffHudY))
        {
            cfg.staffHudX = shX;
            cfg.staffHudY = shY;
            cfg.save();
        }

        drawHud(g, mc, hudRect(mc, g.guiWidth(), false), false);
    }

    /**
     * Bản xem trước vẽ trong menu (kể cả khi chưa có staff online) — để nắm kéo đi chỗ khác.
     * Chỉ vẽ khi Staff HUD đang BẬT: tắt công tắc là thẻ biến mất ngay trong menu luôn.
     */
    public static void renderPreview(GuiGraphics g)
    {
        Minecraft mc = Minecraft.getInstance();

        if (AutoSellVdmConfig.get().staffHudEnabled == false)
        {
            return;
        }
        drawHud(g, mc, hudRect(mc, g.guiWidth(), true), true);
    }

    // 4 mod (litematica / autosellvdm / spawnerprotect / automine) cùng chạy là 4 thẻ staff y hệt
    // nhau chồng lên nhau — và tắt HUD ở mod này vẫn thấy thẻ của mod kia, trông như "tắt không ăn".
    // Nên các mod chia nhau MỘT suất vẽ qua System property (kênh chung cả JVM): mod nào vẽ trước
    // giữ suất + đóng "nhịp tim"; mod giữ suất mà tắt HUD (hoặc đứng hình >1s) thì nhả cho mod khác.
    private static final String HUD_OWNER_KEY = "vdm.staffhud.owner";
    private static final String HUD_BEAT_KEY = "vdm.staffhud.beat";
    private static final String HUD_MOD_ID = "autosellvdm";

    private static boolean claimHud()
    {
        long now = System.currentTimeMillis();
        String owner = System.getProperty(HUD_OWNER_KEY, "");
        long beat;

        try { beat = Long.parseLong(System.getProperty(HUD_BEAT_KEY, "0")); }
        catch (NumberFormatException e) { beat = 0L; }

        if (owner.isEmpty() == false && owner.equals(HUD_MOD_ID) == false && now - beat < 1000L)
        {
            return false;
        }

        System.setProperty(HUD_OWNER_KEY, HUD_MOD_ID);
        System.setProperty(HUD_BEAT_KEY, Long.toString(now));
        return true;
    }

    private static void releaseHud()
    {
        if (HUD_MOD_ID.equals(System.getProperty(HUD_OWNER_KEY, "")))
        {
            System.setProperty(HUD_OWNER_KEY, "");
        }
    }

    private static void drawHud(GuiGraphics g, Minecraft mc, int[] r, boolean preview)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        var font = mc.font;

        card(g, r[0], r[1], r[2], r[3]);
        int x = r[0] + 8;
        int ly = r[1] + 5;
        g.drawString(font, HUD_HEAD, x, ly, 0xFFFFFFFF);
        ly += 13;

        for (net.minecraft.network.chat.Component c : hudLines(preview))
        {
            g.drawString(font, c, x, ly, 0xFFFFFFFF);
            ly += 11;
        }
        if (cfg.autoSignEnabled)
        {
            g.drawString(font, signLine(), x, ly, 0xFF9BA1AB);
            ly += 11;
        }

        // Dòng cuối: khung này là của mod nào — tắt/di chuyển thì biết chỉnh ở đâu.
        g.drawString(font, modLine(), x, ly, 0xFF61666E);
        ly += 11;

        if (preview)
        {
            g.drawString(font, HUD_HINT, x, ly, 0xFF61666E);
        }
    }

    /** Thẻ bo góc tự chứa (nền tối + viền xám như mẫu) — không phụ thuộc theme của màn nào. */
    private static void card(GuiGraphics g, int x, int y, int w, int h)
    {
        int r = 6;
        int bg = 0xE6141418;
        int border = 0xFF33353C;

        for (int dy = 0; dy < h; dy++)
        {
            int inset = 0;
            if (dy < r || dy >= h - r)
            {
                double cy = dy < r ? (r - 0.5) - dy : dy - (h - r - 0.5);
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - cy * cy)));
            }
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, bg);
            if (dy == 0 || dy == h - 1)
            {
                g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, border);
            }
            else if (inset > 0)
            {
                g.fill(x + inset - 1, y + dy, x + inset, y + dy + 1, border);
                g.fill(x + w - inset, y + dy, x + w - inset + 1, y + dy + 1, border);
            }
            else
            {
                g.fill(x, y + dy, x + 1, y + dy + 1, border);
                g.fill(x + w - 1, y + dy, x + w, y + dy + 1, border);
            }
        }
    }
}


