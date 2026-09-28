/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;

/**
 * Bảng màu + mấy hàm vẽ dùng chung cho toàn bộ menu của mod, theo đúng kiểu
 * ClickGUI của AutoMine (mẫu: Dawn Client): nền đen, viền xám mảnh, bo góc,
 * chữ TẮT xám — BẬT trắng. Không neon, không gradient, không texture vanilla.
 *
 * <p>Các widget vanilla (nút, ô nhập) được vẽ lại trong
 * {@code mixin.MixinAbstractButton} / {@code mixin.MixinEditBox}; chúng chỉ đổi
 * kiểu khi màn hình đang mở có đánh dấu {@link VdmStyledScreen}, nên GUI của
 * vanilla và của mod khác không bị đụng tới.
 */
public final class VdmTheme
{
    /** Lớp phủ tối toàn màn hình sau khi làm mờ thế giới. */
    public static final int SCRIM = 0xD9070809;
    /** Nền thẻ lớn ôm toàn bộ nội dung. */
    public static final int SURFACE = 0xF20E0F12;
    public static final int BORDER = 0xFF26282E;
    public static final int BORDER_LIGHT = 0xFF3A3D45;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int TEXT = 0xFF9BA1AB;
    public static final int TEXT_ON = 0xFFE8EAEE;
    public static final int TEXT_DIM = 0xFF61666E;
    public static final int BTN_BG = 0xFF26262B;
    public static final int BTN_BG_HOVER = 0xFF303036;
    public static final int BTN_BG_OFF = 0xFF1E1E23;
    public static final int FIELD_BG = 0xFF232328;
    /** Nền thẻ nhóm — sáng hơn nền chung vừa đủ để thấy khối. */
    public static final int GROUP_BG = 0x26161A20;
    public static final int SEPARATOR = 0x2EFFFFFF;
    /**
     * Màu nhấn tím của client, dùng RẤT tiết chế: ô nhập đang gõ, nút đang rê
     * chuột, gạch dưới tiêu đề. Bản trước chỉ có trắng–xám nên phẳng lì, không
     * có gì cho mắt bám vào ("không có điểm nhấn gì cả").
     */
    public static final int ACCENT = 0xFFEDEEF2;
    private static final int ACCENT_FAINT = 0x59FFFFFF;

    /**
     * Font chữ riêng của menu: Droid Sans (TTF, Apache-2.0) đóng kèm trong
     * {@code assets/autosellvdm/font/sleek.json} — chữ mềm và tây hơn hẳn font
     * pixel của vanilla, lại đủ dấu tiếng Việt.
     *
     * <p>File font khai báo thêm ba provider gốc của vanilla ở SAU, nên ký tự nào
     * Roboto không có (biểu tượng ⛏ ⚙ ☰ ♪ …) vẫn rơi về font mặc định —
     * {@code FontSet} lấy provider ĐẦU TIÊN có glyph, không phải cái cuối.
     */
    public static final FontDescription FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("autosellvdm", "sleek"));

    /** Nhớ sẵn lớp nào là màn hình của mod, khỏi soi interface mỗi khung hình. */
    private static final java.util.Map<Class<?>, Boolean> MOD_SCREENS = new java.util.WeakHashMap<>();

    private VdmTheme() {}

    /**
     * Màn hình đang mở có phải của bộ mod này không — nhận cả màn hình của mod
     * ANH EM (AutoSellVDM ↔ Litematica fork ↔ AutoMine), không chỉ của chính mình.
     *
     * <p>Lý do phải soi theo TÊN interface thay vì {@code instanceof}: hai mod
     * đều vá cùng một lời gọi vẽ khung của {@code EditBox}, mà Mixin chỉ cho
     * MỘT {@code @Redirect} trên một lời gọi — cái thua bị bỏ qua. Nên cái nào
     * thắng cũng phải biết tô cho màn hình của cả hai, không thì một trong hai
     * mod mất kiểu. (Bản trước mỗi mod chỉ nhận màn hình của mình và
     * {@code require} mặc định = 1, nên cái thua ném {@code InjectionError} →
     * crash ngay lúc khởi động.)
     */
    public static boolean isModScreen(Object screen)
    {
        if (screen == null)
        {
            return false;
        }
        if (screen instanceof VdmStyledScreen)
        {
            return true;
        }

        Class<?> type = screen.getClass();
        Boolean known = MOD_SCREENS.get(type);

        if (known != null)
        {
            return known;
        }

        boolean found = false;

        for (Class<?> c = type; c != null && found == false; c = c.getSuperclass())
        {
            for (Class<?> itf : c.getInterfaces())
            {
                String name = itf.getSimpleName();

                if (name.equals("VdmStyledScreen") || name.equals("StyledScreen"))
                {
                    found = true;
                    break;
                }
            }
        }

        MOD_SCREENS.put(type, found);
        return found;
    }

    /** Khối chữ nhật bo bốn góc, tô đặc. */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color)
    {
        if (w <= 0 || h <= 0)
        {
            return;
        }
        r = Math.min(r, Math.min(w / 2, h / 2));

        if (r <= 0)
        {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h - 2 * r > 0)
        {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int dy = 0; dy < r; dy++)
        {
            double cy = (r - 0.5) - dy;
            int inset = (int) Math.round(r - Math.sqrt((double) r * r - cy * cy));

            if (inset < 0)
            {
                inset = 0;
            }
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            g.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, color);
        }
    }

    /** Viền mảnh 1px chạy quanh thẻ bo góc. */
    public static void roundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color)
    {
        if (w <= 0 || h <= 0)
        {
            return;
        }
        r = Math.min(r, Math.min(w / 2, h / 2));

        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);

        for (int dy = 0; dy < r; dy++)
        {
            double cy = (r - 0.5) - dy;
            int inset = (int) Math.round(r - Math.sqrt((double) r * r - cy * cy));

            if (inset < 0)
            {
                inset = 0;
            }
            g.fill(x + inset, y + dy, x + inset + 1, y + dy + 1, color);
            g.fill(x + w - inset - 1, y + dy, x + w - inset, y + dy + 1, color);
            g.fill(x + inset, y + h - 1 - dy, x + inset + 1, y + h - dy, color);
            g.fill(x + w - inset - 1, y + h - 1 - dy, x + w - inset, y + h - dy, color);
        }
    }

    /**
     * Nền chung của mọi menu — ĐÃ ĐỔI theo phong cách ClickGUI thẻ nổi (mẫu AutoMine): chỉ một lớp
     * phủ RẤT nhẹ để chữ nổi, KHÔNG còn tấm thẻ đen ôm cả màn hình — các khối {@link #group} tự
     * đứng thành thẻ nổi trên thế giới.
     */
    public static void backdrop(Screen screen, GuiGraphics g)
    {
        g.fill(0, 0, screen.width, screen.height, 0x50060708);
    }

    /** Tiêu đề canh giữa: chữ trắng + gạch dưới có đoạn giữa màu nhấn. */
    public static void title(Screen screen, GuiGraphics g, Font font, String text, int y)
    {
        g.drawCenteredString(font, Component.literal(text), screen.width / 2, y, TITLE);
        int mid = screen.width / 2;
        int half = Math.max(40, font.width(text) / 2 + 20);
        g.fill(24, y + 12, screen.width - 24, y + 13, SEPARATOR);
        g.fill(mid - half, y + 12, mid + half, y + 13, ACCENT_FAINT);
    }

    /**
     * Cắt chuỗi cho vừa {@code maxWidth} px, thừa thì thay đuôi bằng "…" — hiểu cả mã màu §
     * trong chuỗi (đo bằng {@link Font#width}, cắt bằng {@code substrByWidth} nên mã màu ở
     * phần giữ lại vẫn nguyên). MỌI chuỗi động vẽ trong GUI phải đi qua đây để không tràn thẻ.
     */
    public static String ellipsize(Font font, String text, int maxWidth)
    {
        if (text == null)
        {
            return "";
        }
        if (font.width(text) <= maxWidth)
        {
            return text;
        }
        int room = Math.max(0, maxWidth - font.width("…"));
        return font.substrByWidth(FormattedText.of(text), room).getString() + "…";
    }

    /** Bản {@link Component} của {@link #ellipsize(Font, String, int)} — cho nhãn nút vanilla. */
    public static Component ellipsize(Font font, Component label, int maxWidth)
    {
        if (label == null)
        {
            return Component.empty();
        }
        if (font.width(label) <= maxWidth)
        {
            return label;
        }
        // Nhãn của mod đều là literal + mã màu §, nên getString() giữ nguyên mã màu.
        return Component.literal(ellipsize(font, label.getString(), maxWidth));
    }

    /** Nút phẳng — dùng bởi mixin, nên mọi nút vanilla trong menu của mod đều ăn kiểu này. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label,
            boolean hovered, boolean active)
    {
        int bg = !active ? BTN_BG_OFF : hovered ? BTN_BG_HOVER : BTN_BG;
        roundedRect(g, x, y, w, h, 4, bg);
        // Rê chuột thì viền sáng lên màu nhấn — phản hồi duy nhất cho biết nút
        // nào đang chỉ vào, vì nhãn đã mang sẵn màu §a/§c của trạng thái.
        roundedBorder(g, x, y, w, h, 4, !active ? BORDER : hovered ? ACCENT : BORDER);
        // Nhãn thường có mã màu § sẵn (§aON / §cOFF); màu ở đây chỉ là mặc định.
        // Nhãn dài hơn nút thì cắt "…" — chữ không bao giờ được tràn ra ngoài nút.
        g.drawCenteredString(font, ellipsize(font, label, w - 8), x + w / 2, y + (h - 8) / 2,
                active ? TEXT_ON : TEXT_DIM);
    }

    /** Khung ô nhập — vẽ ĐÚNG ô của widget, thay cho texture khung của vanilla. */
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused)
    {
        roundedRect(g, x, y, w, h, 4, FIELD_BG);
        roundedBorder(g, x, y, w, h, 4, focused ? ACCENT : BORDER);
    }

    /**
     * Thẻ nhóm — giờ là THẺ NỔI thật sự (kiểu panel AutoMine): bóng đổ mềm + nền đen trong mờ +
     * viền xám, vì backdrop không còn tấm surface chung để tựa vào nữa.
     */
    public static void group(GuiGraphics g, int x, int y, int w, int h)
    {
        roundedRect(g, x + 1, y + 2, w, h, 8, 0x59000000);
        roundedRect(g, x, y, w, h, 8, 0xD91E1E22);
        roundedBorder(g, x, y, w, h, 8, 0x14FFFFFF);
    }

    /** Nhãn nhóm nhỏ, chữ xám nhạt — đặt ở mép trên thẻ. */
    public static void groupLabel(GuiGraphics g, Font font, String text, int x, int y)
    {
        g.drawString(font, Component.literal(text), x, y, TEXT_DIM, false);
    }
}
