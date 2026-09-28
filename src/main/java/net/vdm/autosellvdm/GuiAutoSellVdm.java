/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * Màn chính của AutoSellVDM — ClickGUI thẻ nổi (chuẩn theo menu AutoMine mà user chọn làm mẫu):
 * 4 panel kéo-thả §fBán · Discord · Auto Pay · Kết nối§r trên nền blur, không phủ tối. Bộ chọn vật
 * phẩm chuyển sang màn riêng {@link GuiItemsVdm}; màn Cảnh báo và ⏱ Tốc độ mở từ panel Kết nối.
 *
 * <p>Ô chữ lưu khi đóng menu (onClose/removed) hoặc qua dòng "💾 Lưu config"; toggle/stepper/phím
 * lưu ngay khi bấm ({@link #onValueChanged}).
 */
public class GuiAutoSellVdm extends VdmPanelScreen
{
    private EditBox commandField;
    private EditBox valueField;
    private EditBox webhookField;
    private EditBox webhookNameField;
    private EditBox userIdField;
    private EditBox avatarField;
    private EditBox marksField;
    private EditBox payTargetField;
    private EditBox payAmountField;
    private EditBox payKeepField;
    private EditBox payBalCmdField;
    private EditBox payCmdField;
    private EditBox reconnectHoursField;
    private EditBox signTextField;

    /** Dòng phản hồi tạm (đã lưu / lý do test không chạy), tự tắt sau ít giây. */
    private String flash = "";
    private long flashUntilMs;

    public GuiAutoSellVdm(Screen parent)
    {
        super("Auto Bán", parent);
    }

    @Override
    protected String loadPositions()
    {
        return AutoSellVdmConfig.get().guiPanelsMain2;
    }

    @Override
    protected void savePositions(String value)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.guiPanelsMain2 = value;
        cfg.save();
    }

    @Override
    protected void onValueChanged()
    {
        AutoSellVdmConfig.get().save();
    }

    @Override
    protected String headerStatus()
    {
        if (AutoSellVdmConfig.get().autoSellEnabled == false)
        {
            return "đang tắt";
        }
        return AutoSell.getInstance().isWaitingForItems() ? "chờ đồ" : "đang bán";
    }

    private void showFlash(String text)
    {
        this.flash = text;
        this.flashUntilMs = System.currentTimeMillis() + 3000L;
    }

    private String flashText()
    {
        return System.currentTimeMillis() < this.flashUntilMs ? this.flash : "";
    }

    @Override
    protected void buildPanels()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        // --- Bán ------------------------------------------------------
        this.commandField = this.makeField(cfg.autoSellCommand, "sell", 64);
        this.valueField = this.makeField(num(cfg.autoSellValuePerSale), "1", 64);

        Panel sell = new Panel("Bán", "$");
        sell.add(new ToggleEntry("Tự bán", () -> cfg.autoSellEnabled,
                v -> AutoSell.getInstance().setEnabled(v)));
        sell.add(new ToggleEntry("Thống kê", () -> cfg.autoSellStats, v ->
        {
            cfg.autoSellStats = v;
            if (v)
            {
                SellStats.reset();
            }
        }));
        sell.add(new ToggleEntry("Tiền từ chat", () -> cfg.autoSellChatParsing,
                v -> cfg.autoSellChatParsing = v));
        sell.add(new GapEntry());
        sell.add(new FieldEntry("Lệnh bán (không cần /)", this.commandField));
        sell.add(new StepperEntry("Số ô bán", () -> cfg.autoSellAreaSlots,
                v -> cfg.autoSellAreaSlots = clamp(v, 1, 108), 1));
        sell.add(new StepperEntry("Ô xác nhận", () -> cfg.autoSellConfirmSlot,
                v -> cfg.autoSellConfirmSlot = clamp(v, -1, 107), 1));
        sell.add(new StepperEntry("Delay nhét (tick)", () -> cfg.autoSellDelay,
                v -> cfg.autoSellDelay = clamp(v, 1, 40), 1));
        sell.add(new FieldEntry("Giá mỗi lượt (tắt tiền chat)", this.valueField));
        sell.add(new GapEntry());
        sell.add(new ButtonEntry(() -> "🧺 Chọn vật phẩm (" + AutoSell.listedItems().size() + ") ▸", () ->
        {
            this.saveFields();
            if (this.minecraft != null)
            {
                this.minecraft.setScreen(new GuiItemsVdm(this));
            }
        }, null));
        sell.add(new KeyEntry("Phím bật/tắt bán", () -> cfg.autoSellToggleHotkey,
                v -> cfg.autoSellToggleHotkey = v));
        sell.add(new KeyEntry("Phím thống kê", () -> cfg.autoSellStatsToggleHotkey,
                v -> cfg.autoSellStatsToggleHotkey = v));
        sell.add(new GapEntry());
        sell.add(new InfoEntry(this::rhythmText));
        sell.add(new InfoEntry(() -> cfg.autoSellStats && SellStats.isTracking()
                ? "Thu " + SellStats.formatCurrency(SellStats.getTotalEarned())
                        + " · " + SellStats.formatCurrency(SellStats.getEarnedPerHour()) + "/h · "
                        + SellStats.formatDuration()
                : ""));
        this.panels.add(sell);

        // --- Discord --------------------------------------------------
        this.webhookField = this.makeField(cfg.autoSellWebhook, "https://discord.com/api/webhooks/...", 256);
        this.webhookNameField = this.makeField(cfg.autoSellWebhookName, "Tên báo cáo", 64);
        this.userIdField = this.makeField(cfg.autoSellWebhookUserId, "Discord ID (tag)", 32);
        this.avatarField = this.makeField(cfg.autoSellWebhookAvatar, "Ảnh đại diện (URL)", 256);
        this.marksField = this.makeField(cfg.autoSellReportMarks, "1,15,30,60", 64);

        Panel discord = new Panel("Discord", "✉");
        discord.add(new ToggleEntry("Gửi báo cáo webhook", () -> cfg.autoSellWebhookEnabled, v ->
        {
            cfg.autoSellWebhookEnabled = v;
            if (v)
            {
                AutoSell.getInstance().resetReportSchedule();
            }
        }));
        discord.add(new ButtonEntry(() -> "Gửi thử ngay", () ->
        {
            this.saveFields();
            AutoSellWebhook.sendTest();
        }, null));
        discord.add(new InfoEntry(() ->
        {
            int mins = AutoSell.getInstance().minutesToNextReport();
            return mins < 0 ? "" : "Kỳ tới: " + mins + " phút";
        }));
        discord.add(new GapEntry());
        discord.add(new FieldEntry("Webhook URL", this.webhookField));
        discord.add(new FieldEntry("Tên báo cáo", this.webhookNameField));
        discord.add(new FieldEntry("Discord ID (để tag)", this.userIdField));
        discord.add(new FieldEntry("Ảnh đại diện (URL)", this.avatarField));
        discord.add(new FieldEntry("Mốc báo cáo (phút, phẩy ngăn)", this.marksField));
        this.panels.add(discord);

        // --- Auto Pay -------------------------------------------------
        this.payTargetField = this.makeField(cfg.payTarget, "Người nhận", 32);
        this.payAmountField = this.makeField(num(cfg.payAmount), "50m, 1.5b...", 32);
        this.payKeepField = this.makeField(num(cfg.payKeep), "0", 32);
        this.payBalCmdField = this.makeField(cfg.payBalanceCommand, "bal", 64);
        this.payCmdField = this.makeField(cfg.payCommand, "pay %target% %amount%", 64);

        Panel pay = new Panel("Auto Pay", "➤");
        pay.add(new ToggleEntry("Tự chuyển tiền", () -> cfg.payEnabled,
                v -> AutoPay.getInstance().setEnabled(v)));
        pay.add(new FieldEntry("Người nhận", this.payTargetField));
        pay.add(new FieldEntry("Số tiền mỗi lần (50m, 1.5b)", this.payAmountField));
        pay.add(new ToggleEntry("Trả hết trên mức giữ", () -> cfg.payAllAboveKeep,
                v -> cfg.payAllAboveKeep = v));
        pay.add(new FieldEntry("Giữ lại (keep)", this.payKeepField));
        pay.add(new FieldEntry("Lệnh xem số dư", this.payBalCmdField));
        pay.add(new FieldEntry("Lệnh chuyển tiền", this.payCmdField));
        pay.add(new KeyEntry("Phím bật/tắt Pay", () -> cfg.payHotkey, v -> cfg.payHotkey = v));
        pay.add(new InfoEntry(() -> "Số dư: " + (AutoPay.getInstance().getLastBalance() < 0
                ? "chưa rõ" : AutoPay.formatMoney(AutoPay.getInstance().getLastBalance()))
                + " · " + AutoPay.getInstance().statusText()));
        this.panels.add(pay);

        // --- Kết nối --------------------------------------------------
        this.reconnectHoursField = this.makeField(num(cfg.reconnectHours), "2", 16);

        Panel net = new Panel("Kết nối", "⟳");
        net.add(new ToggleEntry("Auto Reconnect theo giờ", () -> cfg.reconnectEnabled,
                v -> cfg.reconnectEnabled = v));
        net.add(new FieldEntry("Chơi mấy giờ thì vào lại", this.reconnectHoursField));
        net.add(new StepperEntry("Chờ rồi vào (giây)", () -> cfg.reconnectDelaySeconds,
                v -> cfg.reconnectDelaySeconds = clamp(v, 1, 120), 1));
        net.add(new InfoEntry(() -> AutoReconnect.getInstance().statusText()));
        net.add(new ButtonEntry(() -> "⏻ Test out ngay", () ->
        {
            this.saveFields();
            if (this.minecraft != null)
            {
                String problem = AutoReconnect.getInstance().testNow(this.minecraft);
                if (problem != null)
                {
                    this.showFlash(problem);
                }
            }
        }, null));
        net.add(new GapEntry());
        net.add(new ButtonEntry(() -> "⚠ Cảnh báo người chơi ▸", () ->
        {
            this.saveFields();
            if (this.minecraft != null)
            {
                this.minecraft.setScreen(new GuiAlertVdm(this));
            }
        }, null));
        net.add(new ButtonEntry(() -> "⏱ Tốc độ các tính năng ▸", () ->
        {
            this.saveFields();
            if (this.minecraft != null)
            {
                this.minecraft.setScreen(new GuiSpeedVdm(this));
            }
        }, null));
        net.add(new GapEntry());
        net.add(new ButtonEntry(() -> "💾 Lưu config", () ->
        {
            this.saveFields();
            this.showFlash(savedSummary());
        }, null));
        net.add(new InfoEntry(this::flashText));
        this.panels.add(net);

        // --- Bảo vệ (No-Render + Staff + Auto Sign) -------------------
        this.signTextField = this.makeField(cfg.signText, "4 dòng cách nhau dấu |", 70);

        Panel guard = new Panel("Bảo vệ", "🛡");
        guard.add(new ToggleEntry("No-Render", () -> cfg.noRenderEnabled,
                v -> cfg.noRenderEnabled = v));
        guard.add(new GapEntry());
        guard.add(new ToggleEntry("Staff List HUD", () -> cfg.staffHudEnabled,
                v -> cfg.staffHudEnabled = v));
        guard.add(new ToggleEntry("Auto Sign khi staff GẦN", () -> cfg.autoSignEnabled,
                v -> cfg.autoSignEnabled = v));
        guard.add(new StepperEntry("Phạm vi staff (block)", () -> cfg.staffRadius,
                v -> cfg.staffRadius = clamp(v, 1, 128), 1));
        guard.add(new FieldEntry("Chữ trên bảng (| tách dòng)", this.signTextField));
        guard.add(new ButtonEntry(() -> "👥 Danh sách staff (" + cfg.staffNames.size() + ") ▸", () ->
        {
            this.saveFields();
            if (this.minecraft != null)
            {
                this.minecraft.setScreen(new GuiStaffVdm(this));
            }
        }, null));
        guard.add(new InfoEntry(() -> StaffGuard.onlineStaff().isEmpty() ? ""
                : "⚠ Online: " + String.join(", ", StaffGuard.onlineStaff())));
        this.panels.add(guard);
    }

    /** "Nhịp 1 · cần 100% túi (đang 12/36)", hoặc đếm lùi khi đang chủ động đứng im. */
    private String rhythmText()
    {
        if (AutoSellVdmConfig.get().autoSellEnabled == false)
        {
            return "";
        }

        AutoSell sell = AutoSell.getInstance();
        int window = sell.windowSecondsLeft();
        int hold = sell.holdSecondsLeft();

        if (sell.isWaitingForRestart())
        {
            return "Server restart — chờ " + hold + "s";
        }

        if (window > 0)
        {
            return hold > 0
                    ? "Đợt nhét lẻ (còn " + window + "s) — nghỉ " + hold + "s rồi nhét tiếp"
                    : "Đợt nhét lẻ (còn " + window + "s) — gom đủ stack là đổ + bán luôn";
        }

        return "Nhịp " + sell.loadNumber() + " · cần " + sell.loadTargetPercent()
                + "% túi (đang " + AutoSell.filledSlots(Minecraft.getInstance()) + "/36)";
    }

    /** Đọc lại từ CONFIG (không phải từ ô nhập) để câu xác nhận đáng tin. */
    private static String savedSummary()
    {
        String url = AutoSellVdmConfig.get().autoSellWebhook;
        int len = url == null ? 0 : url.length();

        if (len == 0)
        {
            return "Đã lưu! (chưa có webhook)";
        }
        return "Đã lưu! webhook " + len + " ký tự" + (url.startsWith("https://") ? "" : " — thiếu https://");
    }

    // --- Kéo thả thẻ HUD staff (bản xem trước vẽ trong menu) ---------
    private boolean draggingHud;
    private double hudOffX;
    private double hudOffY;

    // --- Kéo thả thẻ "Farm Stats" ------------------------------------
    private boolean draggingStats;
    private double statsOffX;
    private double statsOffY;

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);
        StaffGuard.renderPreview(g);
        // Thẻ "Farm Stats" vẽ đè lên menu để nắm kéo được ngay tại đây.
        AutoSellHud.renderPreview(g);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled)
    {
        // Thẻ "Farm Stats" xét trước: nó vẽ đè lên menu nên phải được quyền ăn cú bấm trước, không
        // thì nút nằm dưới nuốt mất và không tài nào kéo được thẻ.
        int[] card = AutoSellHud.hudRect(net.minecraft.client.Minecraft.getInstance(), true);

        if (card != null
                && event.x() >= card[0] && event.x() <= card[0] + card[2]
                && event.y() >= card[1] && event.y() <= card[1] + card[3])
        {
            this.draggingStats = true;
            this.statsOffX = event.x() - card[0];
            this.statsOffY = event.y() - card[1];
            return true;
        }

        // HUD tắt thì bản xem trước không vẽ, nên vùng nắm kéo cũng phải tắt theo kẻo nuốt chuột oan.
        int[] hud = StaffGuard.hudRect(net.minecraft.client.Minecraft.getInstance(), this.width, true);
        if (AutoSellVdmConfig.get().staffHudEnabled
                && event.x() >= hud[0] && event.x() <= hud[0] + hud[2]
                && event.y() >= hud[1] && event.y() <= hud[1] + hud[3])
        {
            this.draggingHud = true;
            this.hudOffX = event.x() - hud[0];
            this.hudOffY = event.y() - hud[1];
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy)
    {
        if (this.draggingStats)
        {
            AutoSellHud.moveHud((int) (event.x() - this.statsOffX), (int) (event.y() - this.statsOffY),
                    this.width, this.height);
            return true;
        }
        if (this.draggingHud)
        {
            StaffGuard.moveHud((int) (event.x() - this.hudOffX), (int) (event.y() - this.hudOffY),
                    this.width, this.height);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        if (this.draggingStats)
        {
            this.draggingStats = false;
            AutoSellHud.saveHudPos();
            return true;
        }
        if (this.draggingHud)
        {
            this.draggingHud = false;
            StaffGuard.saveHudPos();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose()
    {
        this.saveFields();
        super.onClose();
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

        if (this.commandField != null)
        {
            String cmd = this.commandField.getValue().trim();
            cfg.autoSellCommand = cmd.isEmpty() ? "sell" : cmd;
        }
        cfg.autoSellValuePerSale = Math.max(0, parseDouble(this.valueField, cfg.autoSellValuePerSale));

        if (this.webhookField != null)
        {
            cfg.autoSellWebhook = this.webhookField.getValue().trim();
        }
        if (this.webhookNameField != null)
        {
            cfg.autoSellWebhookName = this.webhookNameField.getValue().trim();
        }
        if (this.userIdField != null)
        {
            cfg.autoSellWebhookUserId = this.userIdField.getValue().replaceAll("[^0-9]", "");
        }
        if (this.avatarField != null)
        {
            cfg.autoSellWebhookAvatar = this.avatarField.getValue().trim();
        }
        if (this.marksField != null)
        {
            String marks = this.marksField.getValue().trim();
            cfg.autoSellReportMarks = marks.isEmpty() ? "1,15,30,60" : marks;
        }

        if (this.payTargetField != null)
        {
            cfg.payTarget = this.payTargetField.getValue().trim();
        }
        cfg.payAmount = Math.max(0, parseDouble(this.payAmountField, cfg.payAmount));
        cfg.payKeep = Math.max(0, parseDouble(this.payKeepField, cfg.payKeep));
        if (this.payBalCmdField != null)
        {
            String cmd = this.payBalCmdField.getValue().trim();
            cfg.payBalanceCommand = cmd.isEmpty() ? "bal" : cmd;
        }
        if (this.payCmdField != null)
        {
            String cmd = this.payCmdField.getValue().trim();
            cfg.payCommand = cmd.isEmpty() ? "pay %target% %amount%" : cmd;
        }

        double hours = parseDouble(this.reconnectHoursField, cfg.reconnectHours);
        cfg.reconnectHours = hours > 0 ? hours : cfg.reconnectHours;

        if (this.signTextField != null)
        {
            String t = this.signTextField.getValue().trim();
            if (t.isEmpty() == false)
            {
                cfg.signText = t;
            }
        }

        cfg.save();
    }

    private static String num(double v)
    {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Hiểu cả 50m / 1.5b / 20k như người chơi hay gõ. */
    private static double parseDouble(EditBox box, double fallback)
    {
        if (box == null)
        {
            return fallback;
        }
        String raw = box.getValue().trim().replace(",", "").replace("_", "");
        if (raw.isEmpty())
        {
            return fallback;
        }
        double mult = 1.0;
        char last = Character.toLowerCase(raw.charAt(raw.length() - 1));
        if (last == 'k' || last == 'm' || last == 'b' || last == 't')
        {
            mult = switch (last)
            {
                case 'k' -> 1_000.0;
                case 'm' -> 1_000_000.0;
                case 'b' -> 1_000_000_000.0;
                default  -> 1_000_000_000_000.0;
            };
            raw = raw.substring(0, raw.length() - 1);
        }
        try
        {
            return Math.max(0.0, Double.parseDouble(raw) * mult);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }
}
