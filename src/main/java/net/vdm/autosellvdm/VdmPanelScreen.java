/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Khung ClickGUI thẻ nổi — cùng ngôn ngữ với menu AutoMine mà user chốt làm mẫu: các panel đen
 * trong mờ bo góc trôi trên thế giới (nền chỉ blur, KHÔNG phủ tối), tiêu đề icon + gạch nhấn tím,
 * mỗi dòng là text thuần (BẬT = tím trên nền sáng nhẹ, TẮT = xám), stepper {@code − n +}, kéo thả
 * panel bằng tiêu đề và nhớ vị trí vào config.
 *
 * <p>Ô nhập chữ vẫn là {@link EditBox} thật (add qua {@code addRenderableWidget} nên gõ/focus chuẩn
 * vanilla); {@link FieldEntry} chỉ NEO nó vào dòng của panel mỗi khung hình. Panel được vẽ trong
 * {@link #renderBackground} nên mọi widget luôn nằm TRÊN panel, và {@link #mouseClicked} hỏi widget
 * trước rồi mới tới panel — hai hệ không giẫm chân nhau.
 */
public abstract class VdmPanelScreen extends Screen implements VdmStyledScreen
{
    protected static final int PANEL_W = 210;
    protected static final int HEADER_H = 24;
    protected static final int PAD = 8;

    // Bảng màu MONOCHROME theo đúng menu mẫu user đưa 2026-08-18 (screenshot "HUD Editor layers"):
    // panel xám đậm trong mờ KHÔNG viền, dòng bật = pill xám sáng + VẠCH TRẮNG nhỏ bên trái,
    // không còn tím — điểm nhấn duy nhất là trắng.
    private static final int PANEL_BG = 0xD91E1E22;
    private static final int PANEL_BORDER = 0x14FFFFFF;
    private static final int PANEL_SHADOW = 0x4D000000;
    private static final int SEPARATOR = 0x1FFFFFFF;
    private static final int TXT = 0xFFC9CBD1;
    private static final int TXT_ON = 0xFFFFFFFF;
    private static final int TXT_DIM = 0xFF8A8D94;
    private static final int ROW_HOVER = 0x17FFFFFF;
    private static final int ACCENT = 0xFFFFFFFF;
    private static final int ROW_ON_BG = 0x30FFFFFF;
    private static final int ACCENT_DIM = 0xFFB9BBC2;

    private final Screen parent;
    protected final List<Panel> panels = new ArrayList<>();
    private Panel dragging;
    /** Ô phím đang chờ bấm (bind phím); null = không nghe. */
    private KeyEntry listeningKey;

    protected VdmPanelScreen(String title, Screen parent)
    {
        super(Component.literal(title));
        this.parent = parent;
    }

    /** Dựng các panel (gọi lại mỗi lần init — config đọc tươi). */
    protected abstract void buildPanels();

    /** Chuỗi "x,y|x,y|..." đã lưu của màn này. */
    protected abstract String loadPositions();

    protected abstract void savePositions(String value);

    /** Gọi sau mỗi thay đổi giá trị từ toggle/stepper/phím — nơi screen ghi config. */
    protected void onValueChanged() {}

    /** Dòng trạng thái xám cạnh tên trong pill tiêu đề. */
    protected String headerStatus()
    {
        return "";
    }

    @Override
    protected void init()
    {
        this.panels.clear();
        this.listeningKey = null;
        this.buildPanels();
        this.applySavedPositions();
    }

    private void applySavedPositions()
    {
        String[] parts = this.loadPositions().split("\\|");

        // Mặc định: xếp LƯỚI chảy trái→phải, hết bề ngang thì xuống hàng DƯỚI panel cao nhất của
        // hàng trên — 5 thẻ về đúng hàng lối ("sắp xếp lại 5 mục cho hẳn hoi") thay vì thẻ cuối
        // lơ lửng giữa màn. Vị trí user đã kéo tay (parts) vẫn đè lên như cũ.
        int flowX = 8;
        int flowY = 32;
        int rowH = 0;

        for (int i = 0; i < this.panels.size(); i++)
        {
            Panel p = this.panels.get(i);

            if (flowX > 8 && flowX + PANEL_W > this.width - 4)
            {
                flowX = 8;
                flowY += rowH + 10;
                rowH = 0;
            }
            p.x = flowX;
            p.y = flowY;
            rowH = Math.max(rowH, p.height());
            flowX += PANEL_W + 8;

            if (i < parts.length)
            {
                String[] xy = parts[i].split(",");
                if (xy.length == 2)
                {
                    try
                    {
                        p.x = Integer.parseInt(xy[0].trim());
                        p.y = Integer.parseInt(xy[1].trim());
                    }
                    catch (NumberFormatException ignored) { }
                }
            }
            p.x = clamp(p.x, 0, Math.max(0, this.width - 40));
            p.y = clamp(p.y, 0, Math.max(0, this.height - HEADER_H));
        }
    }

    private void storePositions()
    {
        StringBuilder sb = new StringBuilder();

        for (Panel p : this.panels)
        {
            if (sb.length() > 0)
            {
                sb.append('|');
            }
            sb.append(p.x).append(',').append(p.y);
        }
        this.savePositions(sb.toString());
    }

    protected static int clamp(int v, int lo, int hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Tạo một EditBox cho {@link FieldEntry} — vị trí do panel neo lại mỗi khung hình. */
    protected EditBox makeField(String value, String hint, int maxLength)
    {
        EditBox box = new EditBox(this.font, 0, 0, PANEL_W - PAD * 2, 16, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        this.addRenderableWidget(box);
        return box;
    }

    /** Nền: chỉ blur như menu AutoMine — KHÔNG phủ tối; panel vẽ ở đây để widget nổi lên trên. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        if (this.minecraft != null && this.minecraft.level != null)
        {
            this.renderBlurredBackground(g);
        }
        else
        {
            super.renderBackground(g, mouseX, mouseY, delta);
        }

        for (Panel p : this.panels)
        {
            p.render(g, this.font, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);

        // Thanh tiêu đề FULL-WIDTH trên cùng như menu mẫu: tên mod trái, trạng thái phải, tiêu đề giữa.
        VdmTheme.roundedRect(g, 9, 9, this.width - 16, 26, 8, PANEL_SHADOW);
        VdmTheme.roundedRect(g, 8, 8, this.width - 16, 26, 8, PANEL_BG);
        g.fill(16, 17, 20, 25, TXT_ON);
        g.drawString(this.font, "AutoSellVDM", 26, 17, TXT_ON);
        g.drawCenteredString(this.font, this.title.getString(), this.width / 2, 17, TXT);
        String stat = this.headerStatus();
        if (stat.isEmpty() == false)
        {
            g.drawString(this.font, stat, this.width - 16 - this.font.width(stat), 17, TXT_DIM);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        // Widget thật (EditBox) ăn click trước — panel không được nuốt cú click vào ô nhập.
        if (super.mouseClicked(event, doubled))
        {
            return true;
        }

        for (int i = this.panels.size() - 1; i >= 0; i--)
        {
            Panel p = this.panels.get(i);

            if (p.inHeader(event.x(), event.y()))
            {
                this.dragging = p;
                p.dragOffX = event.x() - p.x;
                p.dragOffY = event.y() - p.y;
                this.panels.remove(p);
                this.panels.add(p); // kéo lên trên cùng
                return true;
            }
            if (p.inBody(event.x(), event.y()))
            {
                p.click(event.x(), event.y());
                return true; // nuốt click trong panel, khỏi xuyên xuống panel dưới
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
    {
        if (this.dragging != null)
        {
            this.dragging.x = clamp((int) (event.x() - this.dragging.dragOffX), 0, Math.max(0, this.width - 40));
            this.dragging.y = clamp((int) (event.y() - this.dragging.dragOffY), 0, Math.max(0, this.height - HEADER_H));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        if (this.dragging != null)
        {
            this.dragging = null;
            this.storePositions();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (this.listeningKey != null)
        {
            this.listeningKey.set.accept(event.key() == 256 ? -1 : event.key()); // 256 = ESC xoá phím
            this.listeningKey = null;
            this.onValueChanged();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null && this.parent != null)
        {
            this.minecraft.setScreen(this.parent);
        }
        else
        {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    // ------------------------------------------------------------------ panel

    protected final class Panel
    {
        final String title;
        final String icon;
        final List<Entry> entries = new ArrayList<>();
        int x;
        int y;
        double dragOffX;
        double dragOffY;

        public Panel(String title, String icon)
        {
            this.title = title;
            this.icon = icon;
        }

        public Panel add(Entry e)
        {
            this.entries.add(e);
            return this;
        }

        int height()
        {
            int h = HEADER_H + 3;
            for (Entry e : this.entries)
            {
                h += e.height();
            }
            return h + PAD;
        }

        boolean inHeader(double mx, double my)
        {
            return mx >= this.x && mx <= this.x + PANEL_W && my >= this.y && my <= this.y + HEADER_H;
        }

        boolean inBody(double mx, double my)
        {
            return mx >= this.x && mx <= this.x + PANEL_W && my > this.y + HEADER_H && my <= this.y + this.height();
        }

        void render(GuiGraphics g, Font font, int mouseX, int mouseY)
        {
            int h = this.height();

            VdmTheme.roundedRect(g, this.x + 1, this.y + 2, PANEL_W, h, 8, PANEL_SHADOW);
            VdmTheme.roundedRect(g, this.x, this.y, PANEL_W, h, 8, PANEL_BG);
            VdmTheme.roundedBorder(g, this.x, this.y, PANEL_W, h, 8, PANEL_BORDER);
            g.drawString(font, this.icon, this.x + PAD, this.y + 8, TXT, false);
            int titleX = this.x + PAD + font.width(this.icon) + 6;
            g.drawString(font, VdmTheme.ellipsize(font, this.title, this.x + PANEL_W - PAD - titleX),
                    titleX, this.y + 8, TXT_ON);
            g.fill(this.x + PAD, this.y + HEADER_H - 2, this.x + PANEL_W - PAD, this.y + HEADER_H - 1, SEPARATOR);

            int ry = this.y + HEADER_H + 3;
            for (Entry e : this.entries)
            {
                e.render(g, font, this.x, ry, PANEL_W, mouseX, mouseY);
                ry += e.height();
            }
        }

        void click(double mx, double my)
        {
            int ry = this.y + HEADER_H + 3;
            for (Entry e : this.entries)
            {
                if (my >= ry && my < ry + e.height())
                {
                    e.click(mx - this.x, this.x, ry);
                    return;
                }
                ry += e.height();
            }
        }
    }

    // ------------------------------------------------------------------ rows

    protected interface Entry
    {
        int height();

        void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY);

        /** {@code localX} tính từ mép trái panel; x/y là toạ độ tuyệt đối của dòng. */
        default void click(double localX, int x, int y) {}
    }

    private static boolean hovered(int x, int y, int w, int h, int mouseX, int mouseY)
    {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
    }

    /** Dòng bật/tắt: TẮT = chữ xám, BẬT = tím + vạch trái + nền sáng nhẹ. */
    protected final class ToggleEntry implements Entry
    {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        public ToggleEntry(String label, BooleanSupplier get, Consumer<Boolean> set)
        {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        public int height()
        {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            boolean on = this.get.getAsBoolean();
            boolean hover = hovered(x, y, w, this.height(), mouseX, mouseY);

            if (on || hover)
            {
                VdmTheme.roundedRect(g, x + 4, y, w - 8, this.height() - 1, 4, on ? ROW_ON_BG : ROW_HOVER);
            }
            if (on)
            {
                // Vạch TRẮNG nhỏ bên trái — dấu "đang bật" của menu mẫu.
                VdmTheme.roundedRect(g, x + 6, y + 4, 2, this.height() - 9, 1, TXT_ON);
            }
            int tx = x + PAD + (on ? 4 : 0);
            g.drawString(font, VdmTheme.ellipsize(font, this.label, x + w - PAD - tx),
                    tx, y + 4, on || hover ? TXT_ON : TXT);
        }

        @Override
        public void click(double localX, int x, int y)
        {
            this.set.accept(this.get.getAsBoolean() == false);
            onValueChanged();
        }
    }

    /** Dòng hành động; nhãn động, mờ đi khi chưa đủ điều kiện. */
    protected final class ButtonEntry implements Entry
    {
        final Supplier<String> label;
        final Runnable action;
        final BooleanSupplier enabled;

        public ButtonEntry(Supplier<String> label, Runnable action, BooleanSupplier enabled)
        {
            this.label = label;
            this.action = action;
            this.enabled = enabled;
        }

        boolean isEnabled()
        {
            return this.enabled == null || this.enabled.getAsBoolean();
        }

        @Override
        public int height()
        {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            boolean hover = this.isEnabled() && hovered(x, y, w, this.height(), mouseX, mouseY);

            if (hover)
            {
                VdmTheme.roundedRect(g, x + 4, y, w - 8, this.height() - 1, 4, ROW_HOVER);
            }
            g.drawString(font, VdmTheme.ellipsize(font, this.label.get(), w - PAD * 2), x + PAD, y + 4,
                    this.isEnabled() == false ? TXT_DIM : hover ? ACCENT : TXT);
        }

        @Override
        public void click(double localX, int x, int y)
        {
            if (this.isEnabled())
            {
                this.action.run();
            }
        }
    }

    /** Dòng chỉnh số: nhãn trái, {@code − n +} phải. */
    protected final class StepperEntry implements Entry
    {
        final String label;
        final IntSupplier get;
        final IntConsumer set;
        final int step;

        public StepperEntry(String label, IntSupplier get, IntConsumer set, int step)
        {
            this.label = label;
            this.get = get;
            this.set = set;
            this.step = step;
        }

        @Override
        public int height()
        {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            boolean hover = hovered(x, y, w, this.height(), mouseX, mouseY);

            if (hover)
            {
                VdmTheme.roundedRect(g, x + 4, y, w - 8, this.height() - 1, 4, ROW_HOVER);
            }
            // Nhãn chỉ được ăn tới mép vùng "− n +" (bắt đầu ở w−50) trừ 4px thở.
            g.drawString(font, VdmTheme.ellipsize(font, this.label, w - 50 - PAD - 4),
                    x + PAD, y + 4, hover ? TXT_ON : TXT);
            String value = String.valueOf(this.get.getAsInt());
            g.drawString(font, "−", x + w - 44, y + 4, ACCENT_DIM);
            g.drawString(font, value, x + w - 26 - font.width(value) / 2, y + 4, ACCENT);
            g.drawString(font, "+", x + w - 12, y + 4, ACCENT_DIM);
        }

        @Override
        public void click(double localX, int x, int y)
        {
            if (localX >= PANEL_W - 50 && localX <= PANEL_W - 36)
            {
                this.set.accept(this.get.getAsInt() - this.step);
                onValueChanged();
            }
            else if (localX >= PANEL_W - 18)
            {
                this.set.accept(this.get.getAsInt() + this.step);
                onValueChanged();
            }
        }
    }

    /** Dòng gán phím: bấm vào là chờ phím kế tiếp (ESC xoá). */
    protected final class KeyEntry implements Entry
    {
        final String label;
        final IntSupplier get;
        final IntConsumer set;

        public KeyEntry(String label, IntSupplier get, IntConsumer set)
        {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        public int height()
        {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            boolean hover = hovered(x, y, w, this.height(), mouseX, mouseY);
            boolean waiting = listeningKey == this;

            if (hover || waiting)
            {
                VdmTheme.roundedRect(g, x + 4, y, w - 8, this.height() - 1, 4, waiting ? ROW_ON_BG : ROW_HOVER);
            }
            String key = waiting ? "..." : "[" + ItemHotkeyUtil.name(this.get.getAsInt()) + "]";
            // Tên phím dài (VD [LEFT SHIFT]) chiếm chỗ trước, nhãn cắt theo phần còn lại.
            key = VdmTheme.ellipsize(font, key, (w - PAD * 2) / 2);
            g.drawString(font, VdmTheme.ellipsize(font, this.label, w - PAD * 2 - font.width(key) - 6),
                    x + PAD, y + 4, hover ? TXT_ON : TXT);
            g.drawString(font, key, x + w - PAD - font.width(key), y + 4, waiting ? TXT_ON : TXT_DIM);
        }

        @Override
        public void click(double localX, int x, int y)
        {
            listeningKey = this;
            setFocused(null);
        }
    }

    /** Dòng thông tin mờ, không bấm được; chuỗi rỗng thì cao 0 (tự ẩn). */
    protected final class InfoEntry implements Entry
    {
        final Supplier<String> text;

        public InfoEntry(Supplier<String> text)
        {
            this.text = text;
        }

        @Override
        public int height()
        {
            String s = this.text.get();
            return s == null || s.isEmpty() ? 0 : 12;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            String s = this.text.get();

            if (s != null && s.isEmpty() == false)
            {
                g.drawString(font, font.plainSubstrByWidth(s, w - PAD * 2), x + PAD, y + 2, TXT_DIM, false);
            }
        }
    }

    /** Khoảng thở giữa các nhóm dòng. */
    protected static final class GapEntry implements Entry
    {
        @Override
        public int height()
        {
            return 5;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {}
    }

    /** Dòng neo một {@link EditBox}: nhãn nhỏ phía trên, ô nhập bám theo panel. */
    protected final class FieldEntry implements Entry
    {
        final String label;
        final EditBox box;

        public FieldEntry(String label, EditBox box)
        {
            this.label = label;
            this.box = box;
        }

        @Override
        public int height()
        {
            return 31;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY)
        {
            g.drawString(font, VdmTheme.ellipsize(font, this.label, w - PAD * 2), x + PAD, y + 1,
                    TXT_DIM, false);
            this.box.setX(x + PAD);
            this.box.setY(y + 12);
            this.box.setWidth(w - PAD * 2);
        }
    }
}
