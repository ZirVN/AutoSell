/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Menu for the player-proximity alert: a master switch, the detection range, what to do when a stranger
 * shows up (log out / beep / Discord webhook), a rebindable hotkey, and the friend list — anyone on it
 * never triggers the alarm. Names can be typed in or added with one click from the current player list.
 *
 * <p>Ported from the Litematica fork's {@code GuiAlert}; writes into {@link AutoSellVdmConfig} instead
 * of malilib's Configs, so the hotkey is a plain GLFW key code rather than a malilib keybind.
 */
public class GuiAlertVdm extends Screen implements VdmStyledScreen
{
    private final Screen parent;

    private EditBox rangeField;
    private EditBox webhookField;
    private EditBox friendField;
    private Button hotkeyBtn;

    private boolean listeningHotkey = false;
    /** While > 0, the screen shows a short "Đã lưu!" confirmation. Counted down each frame. */
    private int savedFlashTicks;

    private static final int ROW_H = 18;
    /** First friend row. Sits below the button rows (which end at y=114) plus a header line. */
    private static final int LIST_TOP = 140;

    public GuiAlertVdm(Screen parent)
    {
        super(Component.literal("Cảnh Báo Người Chơi"));
        this.parent = parent;
    }

    private int contentWidth()
    {
        return Math.min(640, this.width - 16);
    }

    private int contentX()
    {
        return (this.width - this.contentWidth()) / 2;
    }

    @Override
    protected void init()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        int x0 = this.contentX();
        int fullW = this.contentWidth();
        int quarter = (fullW - 12) / 4;

        // Row 1 — master switch, log out, beep, hotkey.
        this.addRenderableWidget(Button.builder(this.masterLabel(), b ->
        {
            cfg.playerAlertEnabled = cfg.playerAlertEnabled == false;
            b.setMessage(this.masterLabel());
            this.saveFields();
        }).bounds(x0, 22, quarter, 20).build());

        this.addRenderableWidget(Button.builder(toggleLabel("Tự out", cfg.playerAlertLogout), b ->
        {
            cfg.playerAlertLogout = cfg.playerAlertLogout == false;
            b.setMessage(toggleLabel("Tự out", cfg.playerAlertLogout));
            this.saveFields();
        }).bounds(x0 + quarter + 4, 22, quarter, 20).build());

        this.addRenderableWidget(Button.builder(toggleLabel("Kêu bíp", cfg.playerAlertSound), b ->
        {
            cfg.playerAlertSound = cfg.playerAlertSound == false;
            b.setMessage(toggleLabel("Kêu bíp", cfg.playerAlertSound));
            this.saveFields();
        }).bounds(x0 + 2 * (quarter + 4), 22, quarter, 20).build());

        // Click to bind, then press any key (ESC clears).
        this.hotkeyBtn = Button.builder(this.hotkeyLabel(), b ->
        {
            this.listeningHotkey = true;
            this.setFocused(null);
            b.setMessage(this.hotkeyLabel());
        }).bounds(x0 + 3 * (quarter + 4), 22, fullW - 3 * (quarter + 4), 20).build();
        this.addRenderableWidget(this.hotkeyBtn);

        // Row 2 — detection range + the Discord webhook the alert posts to.
        this.rangeField = this.field(x0, 46, quarter,
                String.valueOf(cfg.playerAlertRange), "Tầm phát hiện (block)");
        this.webhookField = this.field(x0 + quarter + 4, 46, fullW - quarter - 4,
                cfg.playerAlertWebhook, "Discord webhook URL", 256);

        // Row 3 — type a name to whitelist it.
        int addW = 80;
        this.friendField = this.field(x0, 70, fullW - addW - 4, "", "Tên bạn bè (không bị out)", 32);

        this.addRenderableWidget(Button.builder(Component.literal("§aThêm"), b -> this.addTypedFriend())
                .bounds(x0 + fullW - addW, 70, addW, 20).build());

        // Row 4 — one-click add for everyone currently on the server, and a wipe.
        int halfW = (fullW - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Thêm tất cả người đang online"), b ->
        {
            this.addOnlinePlayers();
        }).bounds(x0, 94, halfW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§cXoá hết bạn bè"), b ->
        {
            AutoSellVdmConfig.get().clearFriends();
        }).bounds(x0 + halfW + 4, 94, fullW - halfW - 4, 20).build());

        // Explicit save, so the typed range/webhook can be committed without closing the screen.
        // Cặp nút đối xứng qua trục giữa: [Lưu][4px][Đóng].
        this.addRenderableWidget(Button.builder(Component.literal("§aLưu Config"), b ->
        {
            this.saveFields();
            this.savedFlashTicks = 60; // ~3s of "Đã lưu!" feedback
        }).bounds(x0 + fullW / 2 - 102, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Đóng"), b -> this.onClose())
                .bounds(x0 + fullW / 2 + 2, this.height - 28, 100, 20).build());
    }

    private EditBox field(int x, int y, int w, String value, String hint)
    {
        return this.field(x, y, w, value, hint, 64);
    }

    /**
     * Character limit applied BEFORE the value — see the same helper in {@link GuiAutoSellVdm}: raising
     * it afterwards is too late, {@code setValue} has already cut the string down to the old limit.
     */
    private EditBox field(int x, int y, int w, String value, String hint, int maxLength)
    {
        EditBox box = new EditBox(this.font, x + 1, y, w - 2, 20, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        this.addRenderableWidget(box);
        return box;
    }

    private static Component toggleLabel(String label, boolean on)
    {
        return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
    }

    private Component masterLabel()
    {
        return Component.literal(AutoSellVdmConfig.get().playerAlertEnabled
                ? "§aCảnh báo: BẬT" : "§cCảnh báo: TẮT");
    }

    private Component hotkeyLabel()
    {
        if (this.listeningHotkey)
        {
            return Component.literal("§eBấm phím...");
        }
        return Component.literal("Phím: §b" + ItemHotkeyUtil.name(AutoSellVdmConfig.get().playerAlertHotkey));
    }

    private void addTypedFriend()
    {
        String name = this.friendField.getValue().trim();

        if (name.isEmpty())
        {
            return;
        }

        AutoSellVdmConfig.get().addFriend(name);
        this.friendField.setValue("");
    }

    /** Add every player currently in the tab list, so a whole team can be whitelisted in one click. */
    private void addOnlinePlayers()
    {
        Minecraft mc = this.minecraft;

        if (mc == null || mc.getConnection() == null)
        {
            return;
        }

        String self = mc.player != null ? mc.player.getName().getString() : null;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers())
        {
            String name = info.getProfile().name();

            if (name != null && name.isBlank() == false && name.equalsIgnoreCase(self) == false)
            {
                AutoSellVdmConfig.get().addFriend(name);
            }
        }
    }

    private List<String> friendsSorted()
    {
        List<String> friends = new ArrayList<>(AutoSellVdmConfig.get().friends);
        friends.sort(Comparator.comparing(String::toLowerCase));
        return friends;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (this.listeningHotkey)
        {
            AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
            cfg.playerAlertHotkey = event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key();
            cfg.save();
            this.listeningHotkey = false;

            if (this.hotkeyBtn != null)
            {
                this.hotkeyBtn.setMessage(this.hotkeyLabel());
            }
            return true;
        }

        // Enter in the name box adds it, like a chat line.
        if (this.friendField != null && this.friendField.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER))
        {
            this.addTypedFriend();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl)
    {
        List<String> friends = this.friendsSorted();
        int x0 = this.contentX();
        int fullW = this.contentWidth();
        int listW = Math.min(320, fullW);
        int px = x0 + (fullW - listW) / 2;
        int xBtnX = px + listW - 16;
        int maxVisible = Math.max(1, (this.height - LIST_TOP - 60) / ROW_H);

        for (int i = 0; i < friends.size() && i < maxVisible; i++)
        {
            int y = LIST_TOP + i * ROW_H;

            if (event.x() >= xBtnX - 2 && event.x() <= xBtnX + 10 && event.y() >= y && event.y() <= y + 14)
            {
                AutoSellVdmConfig.get().removeFriend(friends.get(i));
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    /** Nền tối kiểu Dawn thay cho lớp phủ vanilla — xem {@link VdmTheme}. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        VdmTheme.backdrop(this, g);
        // Thẻ nhóm ôm nội dung — màn duy nhất trước đây không có, lệch tông với các màn còn lại.
        VdmTheme.group(g, this.contentX() - 8, 16, this.contentWidth() + 16, this.height - 32);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        VdmTheme.title(this, g, this.font, this.title.getString(), 8);

        String status = cfg.playerAlertEnabled
                ? "§aĐang canh — người lạ vào §f" + cfg.playerAlertRange
                        + " block§a là " + (cfg.playerAlertLogout ? "tự out server" : "báo động (không out)")
                : "§7Bật lên để tự out khi có người lạ lại gần. Bạn bè trong danh sách không tính.";

        g.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height - 44, -1);

        if (this.savedFlashTicks > 0)
        {
            this.savedFlashTicks--;
            g.drawCenteredString(this.font, Component.literal("§aĐã lưu config!"),
                                 this.width / 2, this.height - 56, -1);
        }

        List<String> friends = this.friendsSorted();
        int x0 = this.contentX();
        int fullW = this.contentWidth();
        int listW = Math.min(320, fullW);
        int px = x0 + (fullW - listW) / 2;
        int xBtnX = px + listW - 16;
        int maxVisible = Math.max(1, (this.height - LIST_TOP - 60) / ROW_H);

        // Header with a rule under it, so the list never reads as if it were part of the buttons above.
        g.drawString(this.font, "§fBạn bè (" + friends.size() + ")", px, LIST_TOP - 16, 0xFFFFFFFF);
        String hint = "§7bấm X để bỏ";
        // Canh PHẢI theo bề rộng thật — mốc cứng −62 làm chữ thò ~10px qua mép danh sách.
        g.drawString(this.font, hint, px + listW - this.font.width(hint), LIST_TOP - 16, 0xFF888888);
        g.fill(px, LIST_TOP - 4, px + listW, LIST_TOP - 3, 0xFF555555);

        if (friends.isEmpty())
        {
            g.drawString(this.font, "§7(chưa có ai — gõ tên rồi bấm Thêm)", px + 4, LIST_TOP + 4, 0xFF888888);
            return;
        }

        for (int i = 0; i < friends.size() && i < maxVisible; i++)
        {
            int y = LIST_TOP + i * ROW_H;
            boolean hovRow = mouseX >= px && mouseX <= px + listW && mouseY >= y && mouseY <= y + ROW_H - 2;

            if (hovRow)
            {
                g.fill(px, y, px + listW, y + ROW_H - 2, 0x30FFFFFF);
            }

            // Cắt theo BỀ RỘNG pixel thật (font menu không phải 6px/ký tự) — không tràn vào nút X.
            String name = VdmTheme.ellipsize(this.font, friends.get(i), xBtnX - (px + 4) - 6);

            g.drawString(this.font, name, px + 4, y + 5, 0xFFFFFFFF);

            boolean hovX = mouseX >= xBtnX - 2 && mouseX <= xBtnX + 10 && mouseY >= y && mouseY <= y + 14;
            g.drawString(this.font, "X", xBtnX, y + 5, hovX ? 0xFFFF3333 : 0xFFAAAAAA);
        }

        if (friends.size() > maxVisible)
        {
            g.drawString(this.font, "§7+ " + (friends.size() - maxVisible) + " nữa…", px + 4,
                         LIST_TOP + maxVisible * ROW_H + 2, 0xFF888888);
        }
    }

    @Override
    public void onClose()
    {
        this.saveFields();

        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void removed()
    {
        this.saveFields();
        super.removed();
    }

    private void saveFields()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (this.rangeField != null)
        {
            try
            {
                int range = Integer.parseInt(this.rangeField.getValue().trim());
                if (range >= 1)
                {
                    cfg.playerAlertRange = range;
                }
            }
            catch (NumberFormatException ignored) { }
        }
        if (this.webhookField != null)
        {
            cfg.playerAlertWebhook = this.webhookField.getValue().trim();
        }

        cfg.save();
    }
}
