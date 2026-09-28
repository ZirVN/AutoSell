/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 * Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;




/**
 * Sửa danh sách tên staff (dùng chung cho Staff HUD + Auto Sign): gõ tên → Thêm, bấm §cX§r để xoá,
 * "Mặc định" khôi phục 26 tên gốc. Tên đang ONLINE hiện chấm xanh cho dễ đối chiếu.
 */
public class GuiStaffVdm extends Screen implements VdmStyledScreen
{
    private final Screen parent;
    private EditBox nameField;

    private static final int ROW_H = 13;
    private static final int LIST_TOP = 74;

    public GuiStaffVdm(Screen parent)
    {
        super(Component.literal("Danh Sách Staff"));
        this.parent = parent;
    }

    private int contentWidth()
    {
        return Math.min(420, this.width - 16);
    }

    private int contentX()
    {
        return (this.width - this.contentWidth()) / 2;
    }

    /** Hai cột cho đỡ dài: trả về số dòng mỗi cột theo chiều cao màn hình. */
    private int rowsPerColumn()
    {
        return Math.max(5, (this.height - LIST_TOP - 40) / ROW_H);
    }

    @Override
    protected void init()
    {
        int x0 = this.contentX();
        int fullW = this.contentWidth();
        int addW = 70;
        int resetW = 84;

        this.nameField = new EditBox(this.font, x0 + 1, 40, fullW - addW - resetW - 9, 20,
                Component.literal("Tên staff..."));
        this.nameField.setMaxLength(24);
        this.nameField.setHint(Component.literal("Tên staff..."));
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("§aThêm"), b -> this.addName())
                .bounds(x0 + fullW - addW - resetW - 4, 40, addW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§eMặc định"), b ->
        {
            AutoSellVdmConfig.get().resetStaff();
            AutoSellVdmConfig.get().save();
        }).bounds(x0 + fullW - resetW, 40, resetW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Đóng"), b -> this.onClose())
                .bounds(x0 + (fullW - 100) / 2, this.height - 28, 100, 20).build());
    }

    private void addName()
    {
        String name = this.nameField.getValue().trim();

        if (AutoSellVdmConfig.get().addStaff(name))
        {
            AutoSellVdmConfig.get().save();
            this.nameField.setValue("");
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        VdmTheme.backdrop(this, g);
        VdmTheme.group(g, this.contentX() - 8, 16, this.contentWidth() + 16, this.height - 32);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);
        VdmTheme.title(this, g, this.font, this.title.getString(), 8);

        List<String> names = AutoSellVdmConfig.get().staffNames;
        int perCol = this.rowsPerColumn();
        int colW = (this.contentWidth() - 8) / 2;
        int x0 = this.contentX();

        g.drawString(this.font, "§7Tổng: §f" + names.size()
                + " §7— chấm §axanh§7 = đang online", x0, LIST_TOP - 12, 0xFF9BA1AB);

        for (int i = 0; i < names.size() && i < perCol * 2; i++)
        {
            String name = names.get(i);
            int col = i / perCol;
            int x = x0 + col * (colW + 8);
            int y = LIST_TOP + (i % perCol) * ROW_H;
            boolean online = false;

            for (String on : StaffGuard.onlineStaff())
            {
                if (on.equalsIgnoreCase(name))
                {
                    online = true;
                    break;
                }
            }

            int xBtn = x + colW - 10;
            g.drawString(this.font, online ? "§a●" : "§8●", x, y, 0xFFFFFFFF);
            g.drawString(this.font, VdmTheme.ellipsize(this.font, name, xBtn - (x + 11) - 4),
                    x + 11, y, online ? 0xFFFFFFFF : 0xFFC9CBD1);

            boolean hov = mouseX >= xBtn - 2 && mouseX <= xBtn + 8 && mouseY >= y - 1 && mouseY <= y + 10;
            g.drawString(this.font, "X", xBtn, y, hov ? 0xFFFF3333 : 0xFF666A72);
        }

        if (names.size() > perCol * 2)
        {
            g.drawString(this.font, "§8+ " + (names.size() - perCol * 2) + " nữa…",
                    x0, LIST_TOP + perCol * ROW_H + 2, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        List<String> names = new ArrayList<>(AutoSellVdmConfig.get().staffNames);
        int perCol = this.rowsPerColumn();
        int colW = (this.contentWidth() - 8) / 2;
        int x0 = this.contentX();

        for (int i = 0; i < names.size() && i < perCol * 2; i++)
        {
            int col = i / perCol;
            int xBtn = x0 + col * (colW + 8) + colW - 10;
            int y = LIST_TOP + (i % perCol) * ROW_H;

            if (event.x() >= xBtn - 2 && event.x() <= xBtn + 8 && event.y() >= y - 1 && event.y() <= y + 10)
            {
                AutoSellVdmConfig.get().removeStaff(names.get(i));
                AutoSellVdmConfig.get().save();
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose()
    {
        AutoSellVdmConfig.get().save();
        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }
}


