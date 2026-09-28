/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * Màn hình nhập key. Nhập đúng một lần là mod nhớ mãi (lưu ở {@code config/autosellvdm-key.json}),
 * hoàn toàn offline nên không cần mạng và không khoá theo máy.
 */
public class GuiLicenseKey extends Screen
{
    private final Screen parent;
    private EditBox keyBox;

    /** Thông báo kết quả lần nhập gần nhất; đếm ngược theo frame rồi tự ẩn. */
    private String flash = "";
    private int flashTicks;

    public GuiLicenseKey(Screen parent)
    {
        super(Component.literal("AutoSellVDM — Nhập key"));
        this.parent = parent;
    }

    private int contentWidth()
    {
        return Math.min(320, this.width - 20);
    }

    @Override
    protected void init()
    {
        int w = this.contentWidth();
        int x0 = (this.width - w) / 2;
        int y = this.height / 2 - 20;

        this.keyBox = new EditBox(this.font, x0, y, w, 20, Component.literal("Key"));
        this.keyBox.setMaxLength(64);
        this.keyBox.setValue(LicenseKey.storedKey());
        this.keyBox.setHint(Component.literal("Dán key vào đây"));
        this.addRenderableWidget(this.keyBox);
        this.setInitialFocus(this.keyBox);
        y += 28;

        int half = (w - 4) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("§aKích hoạt"), b -> this.submit())
                .bounds(x0, y, half, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Xoá key"), b ->
        {
            LicenseKey.clear();
            this.keyBox.setValue("");
            this.setFlash("§7Đã xoá key đã lưu.");
        }).bounds(x0 + half + 4, y, w - half - 4, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Đóng"), b -> this.onClose())
                .bounds(x0 + (w - 100) / 2, y, 100, 20).build());
    }

    private void submit()
    {
        if (LicenseKey.tryUnlock(this.keyBox.getValue()))
        {
            // Mở khoá xong thì vào thẳng menu chính, không phải bấm thêm lần nữa.
            if (this.minecraft != null)
            {
                this.minecraft.setScreen(new GuiAutoSellVdm(this.parent));
            }
            return;
        }
        this.setFlash("§cKey không hợp lệ.");
    }

    private void setFlash(String text)
    {
        this.flash = text;
        this.flashTicks = 80;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        // Enter = bấm Kích hoạt, để dán key rồi Enter là xong.
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
            && this.keyBox != null && this.keyBox.isFocused())
        {
            this.submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);

        g.drawCenteredString(this.font, this.title, this.width / 2, 24, -1);

        int cy = this.height / 2 - 52;
        g.drawCenteredString(this.font, Component.literal(LicenseKey.status()), this.width / 2, cy, -1);
        g.drawCenteredString(this.font, Component.literal("§7Nhập key một lần, lần sau không phải nhập lại."),
                             this.width / 2, cy + 14, -1);

        if (this.flashTicks > 0)
        {
            this.flashTicks--;
            g.drawCenteredString(this.font, Component.literal(this.flash),
                                 this.width / 2, this.height / 2 + 46, -1);
        }
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }
}
