/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "⏱ Tốc độ" — every wait of every automatic feature, one labelled box each.
 *
 * <p>These numbers used to be constants compiled into the code, which meant a member who found the
 * seller too slow (or a shop that could not keep up with it) had nothing to change. Each knob names the
 * exact thing it paces and its unit, because "delay" on its own is meaningless when six different
 * delays are involved: 20 tick = 1 giây.
 */
public class GuiSpeedVdm extends Screen implements VdmStyledScreen
{
    /** One editable number: its label, where to read it from and where to write it back. */
    private static final class Knob
    {
        final String label;
        final IntSupplier get;
        final IntConsumer set;
        EditBox box;

        Knob(String label, IntSupplier get, IntConsumer set)
        {
            this.label = label;
            this.get = get;
            this.set = set;
        }
    }

    private static final int COLS = 3;

    private final Screen parent;
    private final List<Knob> knobs = new ArrayList<>();

    private EditBox keywordsField;
    private int savedFlashTicks;

    private int gridTop;
    private int rowHeight;

    public GuiSpeedVdm(Screen parent)
    {
        super(Component.literal("Tốc Độ Các Tính Năng Tự Động"));
        this.parent = parent;
    }

    private int contentWidth()
    {
        return Math.min(760, this.width - 16);
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

        this.knobs.clear();

        // --- Nhịp bán ---------------------------------------------------
        this.knobs.add(new Knob("Nhịp 1 · đầy túi (%)",
                () -> cfg.sellPhase1Percent, v -> cfg.sellPhase1Percent = v));
        this.knobs.add(new Knob("Nhịp 2 · nửa túi (%)",
                () -> cfg.sellPhase2Percent, v -> cfg.sellPhase2Percent = v));
        this.knobs.add(new Knob("Nhét lẻ · bán thường đủ (phút)",
                () -> cfg.sellRandomDumpMinutes, v -> cfg.sellRandomDumpMinutes = v));
        this.knobs.add(new Knob("Nhét lẻ · đợt dài (phút)",
                () -> cfg.sellRandomWindowMinutes, v -> cfg.sellRandomWindowMinutes = v));
        this.knobs.add(new Knob("Nhét lẻ · lệch (giây)",
                () -> cfg.sellRandomDumpJitterSeconds, v -> cfg.sellRandomDumpJitterSeconds = v));
        this.knobs.add(new Knob("Nhét lẻ · từ (stack)",
                () -> cfg.sellRandomDumpMinStacks, v -> cfg.sellRandomDumpMinStacks = v));
        this.knobs.add(new Knob("Nhét lẻ · đến (stack)",
                () -> cfg.sellRandomDumpMaxStacks, v -> cfg.sellRandomDumpMaxStacks = v));
        this.knobs.add(new Knob("Nhét lẻ · nghỉ giữa 2 lần (giây)",
                () -> cfg.sellRandomHoldSeconds, v -> cfg.sellRandomHoldSeconds = v));

        // --- Tốc độ bán -------------------------------------------------
        this.knobs.add(new Knob("Kiểm tra túi (tick)",
                () -> cfg.autoSellPollTicks, v -> cfg.autoSellPollTicks = v));
        this.knobs.add(new Knob("Mỗi lần nhét (tick)",
                () -> cfg.autoSellDelay, v -> cfg.autoSellDelay = v));
        this.knobs.add(new Knob("Bấm xác nhận (tick)",
                () -> cfg.autoSellConfirmDelayTicks, v -> cfg.autoSellConfirmDelayTicks = v));
        this.knobs.add(new Knob("Chờ GUI shop mở (tick)",
                () -> cfg.autoSellGuiWaitTicks, v -> cfg.autoSellGuiWaitTicks = v));
        this.knobs.add(new Knob("Trần 1 lượt bán (tick)",
                () -> cfg.autoSellVisitTimeoutTicks, v -> cfg.autoSellVisitTimeoutTicks = v));
        this.knobs.add(new Knob("Nghỉ khi GUI lỗi (tick)",
                () -> cfg.autoSellGuiFailCooldownTicks, v -> cfg.autoSellGuiFailCooldownTicks = v));
        this.knobs.add(new Knob("Chờ sau khi vào lại (tick)",
                () -> cfg.autoSellResumeDelayTicks, v -> cfg.autoSellResumeDelayTicks = v));

        // --- Restart / mất kết nối -------------------------------------
        this.knobs.add(new Knob("Restart · chờ (giây)",
                () -> cfg.restartWaitSeconds, v -> cfg.restartWaitSeconds = v));
        this.knobs.add(new Knob("Restart · thử lại (giây)",
                () -> cfg.restartProbeSeconds, v -> cfg.restartProbeSeconds = v));
        this.knobs.add(new Knob("Bị văng · chờ (giây)",
                () -> cfg.reconnectKickDelaySeconds, v -> cfg.reconnectKickDelaySeconds = v));
        this.knobs.add(new Knob("Thoát · mỗi bước (tick)",
                () -> cfg.reconnectStepTicks, v -> cfg.reconnectStepTicks = v));
        this.knobs.add(new Knob("Thoát · giữ ESC (tick)",
                () -> cfg.reconnectMenuTicks, v -> cfg.reconnectMenuTicks = v));
        this.knobs.add(new Knob("Kết nối · timeout (giây)",
                () -> cfg.reconnectConnectTimeoutSeconds, v -> cfg.reconnectConnectTimeoutSeconds = v));
        this.knobs.add(new Knob("Kết nối · thử lại (giây)",
                () -> cfg.reconnectRetrySeconds, v -> cfg.reconnectRetrySeconds = v));

        // Row 1 — the three switches these numbers belong to.
        int third = (fullW - 8) / 3;
        this.addRenderableWidget(Button.builder(toggle("Nhịp 2 lượt", cfg.sellPhaseEnabled), b ->
        {
            cfg.sellPhaseEnabled = cfg.sellPhaseEnabled == false;
            b.setMessage(toggle("Nhịp 2 lượt", cfg.sellPhaseEnabled));
            cfg.save();
        }).bounds(x0, 22, third, 20).build());

        this.addRenderableWidget(Button.builder(toggle("Nhận restart", cfg.restartDetectEnabled), b ->
        {
            cfg.restartDetectEnabled = cfg.restartDetectEnabled == false;
            b.setMessage(toggle("Nhận restart", cfg.restartDetectEnabled));
            cfg.save();
        }).bounds(x0 + third + 4, 22, third, 20).build());

        this.addRenderableWidget(Button.builder(toggle("Văng thì vào lại", cfg.reconnectOnKick), b ->
        {
            cfg.reconnectOnKick = cfg.reconnectOnKick == false;
            b.setMessage(toggle("Văng thì vào lại", cfg.reconnectOnKick));
            cfg.save();
        }).bounds(x0 + 2 * (third + 4), 22, fullW - 2 * (third + 4), 20).build());

        // Row 2 — the words that mean "server is going down".
        this.keywordsField = new EditBox(this.font, x0 + 1, 56, fullW - 2, 20,
                Component.literal("Từ khoá restart"));
        this.keywordsField.setMaxLength(256);
        this.keywordsField.setValue(cfg.restartKeywords);
        this.keywordsField.setHint(Component.literal("Từ khoá restart, cách nhau bằng dấu phẩy"));
        this.addRenderableWidget(this.keywordsField);

        // The grid of numbers. Row height shrinks on a short window rather than running off the screen.
        this.gridTop = 92;
        int rows = (this.knobs.size() + COLS - 1) / COLS;
        int available = this.height - 34 - this.gridTop;
        this.rowHeight = Math.max(22, Math.min(30, rows > 0 ? available / rows : 30));

        int colW = (fullW - 8) / COLS;

        for (int i = 0; i < this.knobs.size(); i++)
        {
            Knob knob = this.knobs.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x = x0 + col * (colW + 4);
            int y = this.gridTop + row * this.rowHeight;

            knob.box = new EditBox(this.font, x + 1, y + 9, colW - 2, this.rowHeight - 12,
                                   Component.literal(knob.label));
            knob.box.setMaxLength(6);
            knob.box.setValue(String.valueOf(knob.get.getAsInt()));
            this.addRenderableWidget(knob.box);
        }

        // Bottom row — put everything back, commit, leave.
        int btnY = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("§eMặc định"), b -> this.resetDefaults())
                .bounds(x0 + fullW / 2 - 158, btnY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("§aLưu"), b ->
        {
            this.save();
            this.savedFlashTicks = 60;
        }).bounds(x0 + fullW / 2 - 50, btnY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Đóng"), b -> this.onClose())
                .bounds(x0 + fullW / 2 + 58, btnY, 100, 20).build());
    }

    private static Component toggle(String label, boolean on)
    {
        return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
    }

    /** Write every box back, clamp, save, then re-read so a clamped value is visible immediately. */
    private void save()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        for (Knob knob : this.knobs)
        {
            if (knob.box == null)
            {
                continue;
            }
            try
            {
                knob.set.accept(Integer.parseInt(knob.box.getValue().trim()));
            }
            catch (NumberFormatException ignored)
            {
                // Leave the stored value alone; the re-read below puts the old number back on screen.
            }
        }

        if (this.keywordsField != null)
        {
            cfg.restartKeywords = this.keywordsField.getValue().trim();
        }

        cfg.clampNewFields();
        cfg.save();

        for (Knob knob : this.knobs)
        {
            if (knob.box != null)
            {
                knob.box.setValue(String.valueOf(knob.get.getAsInt()));
            }
        }
        if (this.keywordsField != null)
        {
            this.keywordsField.setValue(cfg.restartKeywords);
        }
    }

    /** Restore the shipped numbers — one click out of a setup that no longer sells. */
    private void resetDefaults()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        AutoSellVdmConfig fresh = new AutoSellVdmConfig();

        cfg.sellPhaseEnabled = fresh.sellPhaseEnabled;
        cfg.sellPhase1Percent = fresh.sellPhase1Percent;
        cfg.sellPhase2Percent = fresh.sellPhase2Percent;
        cfg.sellRandomDumpMinutes = fresh.sellRandomDumpMinutes;
        cfg.sellRandomWindowMinutes = fresh.sellRandomWindowMinutes;
        cfg.sellRandomDumpJitterSeconds = fresh.sellRandomDumpJitterSeconds;
        cfg.sellRandomDumpMinStacks = fresh.sellRandomDumpMinStacks;
        cfg.sellRandomDumpMaxStacks = fresh.sellRandomDumpMaxStacks;
        cfg.sellRandomHoldSeconds = fresh.sellRandomHoldSeconds;

        cfg.autoSellPollTicks = fresh.autoSellPollTicks;
        cfg.autoSellDelay = fresh.autoSellDelay;
        cfg.autoSellConfirmDelayTicks = fresh.autoSellConfirmDelayTicks;
        cfg.autoSellGuiWaitTicks = fresh.autoSellGuiWaitTicks;
        cfg.autoSellVisitTimeoutTicks = fresh.autoSellVisitTimeoutTicks;
        cfg.autoSellGuiFailCooldownTicks = fresh.autoSellGuiFailCooldownTicks;
        cfg.autoSellResumeDelayTicks = fresh.autoSellResumeDelayTicks;

        cfg.restartDetectEnabled = fresh.restartDetectEnabled;
        cfg.restartWaitSeconds = fresh.restartWaitSeconds;
        cfg.restartProbeSeconds = fresh.restartProbeSeconds;
        cfg.restartKeywords = fresh.restartKeywords;

        cfg.reconnectOnKick = fresh.reconnectOnKick;
        cfg.reconnectKickDelaySeconds = fresh.reconnectKickDelaySeconds;
        cfg.reconnectStepTicks = fresh.reconnectStepTicks;
        cfg.reconnectMenuTicks = fresh.reconnectMenuTicks;
        cfg.reconnectConnectTimeoutSeconds = fresh.reconnectConnectTimeoutSeconds;
        cfg.reconnectRetrySeconds = fresh.reconnectRetrySeconds;

        cfg.save();
        this.rebuildWidgets();
    }

    /** Nền tối kiểu Dawn — xem {@link VdmTheme}. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        VdmTheme.backdrop(this, g);

        int x0 = this.contentX() - 4;
        int w = this.contentWidth() + 8;

        VdmTheme.group(g, x0, 18, w, 60);   // công tắc + từ khoá

        // Thẻ lưới ôm ĐÚNG số hàng thật — không kéo dài xuống đáy thành hộp rỗng.
        int rows = (this.knobs.size() + COLS - 1) / COLS;
        int gridH = (this.gridTop - 86) + rows * Math.max(1, this.rowHeight) + 4;
        VdmTheme.group(g, x0, 86, w, Math.min(gridH, this.height - 34 - 86));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);
        VdmTheme.title(this, g, this.font, this.title.getString(), 6);

        int x0 = this.contentX();
        int colW = (this.contentWidth() - 8) / COLS;

        // Labels sit above their box: a hint inside the box would vanish the moment a number is typed,
        // which is exactly when the member needs to know what they are editing.
        for (int i = 0; i < this.knobs.size(); i++)
        {
            Knob knob = this.knobs.get(i);
            int x = x0 + (i % COLS) * (colW + 4);
            int y = this.gridTop + (i / COLS) * this.rowHeight;

            g.drawString(this.font, VdmTheme.ellipsize(this.font, knob.label, colW - 6),
                    x + 2, y, 0xFF9BA1AB, false);
        }

        if (this.savedFlashTicks > 0)
        {
            this.savedFlashTicks--;
            g.drawCenteredString(this.font, Component.literal("§aĐã lưu tốc độ!"),
                                 this.width / 2, this.height - 38, -1);
        }
    }

    @Override
    public void onClose()
    {
        this.save();

        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void removed()
    {
        this.save();
        super.removed();
    }
}
