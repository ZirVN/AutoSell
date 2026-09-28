/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * All settings for AutoSellVDM in one {@code config/autosellvdm.json}. Gathers the Auto Sell,
 * Auto Pay, Auto Reconnect and Player Alert fields that used to live in Litematica's Configs /
 * AutoPayConfig.
 */
public class AutoSellVdmConfig
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autosellvdm.json");
    private static AutoSellVdmConfig INSTANCE;

    // --- Auto Sell ---
    public boolean autoSellEnabled = false;
    public String autoSellCommand = "sell";
    public boolean autoSellStats = true;
    public String autoSellWebhook = "";
    public String autoSellReportMarks = "1,15,30,60";
    /** Ticks between the macro's own clicks — the human-speed pacing knob. */
    public int autoSellDelay = 5;
    /** Slot clicked to confirm the sale in the server's GUI (-1 = don't click anything). */
    public int autoSellConfirmSlot = 53;
    /** How many of the container's leading slots count as the "sell area" items are moved into. */
    public int autoSellAreaSlots = 45;
    /**
     * LEGACY: the old manual/auto switch. The manual mode was removed — the seller always drives the
     * whole cycle itself now. Kept only so Gson can still read old config files without complaint.
     */
    @Deprecated
    public boolean autoSellAutoCommand = true;
    /** Read earnings out of chat. Off falls back to {@link #autoSellValuePerSale} per sale. */
    public boolean autoSellChatParsing = true;
    public double autoSellValuePerSale = 1.0;
    /** Discord ping target, shared by the sell report and the player alert. Digits only. */
    public String autoSellWebhookUserId = "";
    public String autoSellWebhookName = "Auto Bán";
    public String autoSellWebhookAvatar = "";
    /** Post the periodic report to Discord. Separate from {@link #autoSellStats} counting locally. */
    public boolean autoSellWebhookEnabled = false;
    /** true = only the listed items are sold; false = everything EXCEPT them. */
    public boolean autoSellWhitelist = true;
    /** Item ids ("minecraft:dispenser") the mode above applies to. */
    public List<String> autoSellItems = new ArrayList<>();
    /** GLFW key code that toggles Auto Sell in-game (-1 = unset). Any key on the keyboard. */
    public int autoSellToggleHotkey = -1;
    /** GLFW key code that toggles the stats counters, resetting them when switched on (-1 = unset). */
    public int autoSellStatsToggleHotkey = -1;

    // ------------------------------------------------------------------
    // Nhịp bán — "nhịp 1 đầy kho, nhịp 2 nửa kho rồi bán"
    //
    // Kho /sell của server có 45 ô, túi người chơi có 36 ô: một túi đầy chỉ lấp
    // được 36/45 ô, nên phải đổ HAI lượt mới đầy một kho. Lượt 2 chỉ cần 9 ô là
    // đủ, vì thế nó mở cổng sớm hơn (nửa túi) thay vì bắt chờ đầy lần nữa.
    // ------------------------------------------------------------------

    /** Bật nhịp 2 lượt. Tắt = lượt nào cũng đòi đủ {@link #sellPhase1Percent}. */
    public boolean sellPhaseEnabled = true;
    /** Lượt 1: túi phải đầy bao nhiêu phần trăm mới đổ (mặc định 100% = 36/36 ô). */
    public int sellPhase1Percent = 100;
    /** Lượt 2: chỉ cần nửa túi (18/36 ô) là đổ nốt cho đầy kho rồi bấm bán ngay. */
    public int sellPhase2Percent = 50;

    /** Bán theo nhịp 2 lượt đủ ngần này phút thì tới một ĐỢT nhét lẻ ("phút thứ 16"). 0 = tắt nhét lẻ. */
    public int sellRandomDumpMinutes = 15;
    /** Xê dịch ngẫu nhiên quanh mốc trên (giây), để hai đợt liên tiếp không cách đều nhau. */
    public int sellRandomDumpJitterSeconds = 90;
    /** Mỗi lượt nhét lẻ đổ vào kho từ ngần này stack... */
    public int sellRandomDumpMinStacks = 5;
    /** ...đến ngần này stack (chọn ngẫu nhiên trong khoảng, lời chốt: 5–10). */
    public int sellRandomDumpMaxStacks = 10;
    /** Trong đợt nhét lẻ, giữa hai lượt đổ thì nghỉ ngần này giây (không chặn nhịp thường). */
    public int sellRandomHoldSeconds = 60;
    /** Đợt nhét lẻ kéo dài ngần này phút ("phút 16–18" = 3) rồi quay về nhịp 2 lượt. 0 = tắt. */
    public int sellRandomWindowMinutes = 3;
    /** Đánh dấu đã nâng cấp nhịp theo lời chốt nào — để đổi mặc định một lần mà không đè chỉnh tay sau này. */
    public int rhythmVersion = 0;

    // ------------------------------------------------------------------
    // Tốc độ từng thành phần (tick — 20 tick = 1 giây)
    // ------------------------------------------------------------------

    /** Nhịp kiểm tra túi khi đang chờ đồ. 1 = phát hiện đầy túi ngay tức khắc. */
    public int autoSellPollTicks = 1;
    /** Nghỉ giữa "bấm ô xác nhận" và lần kiểm tra kết quả. */
    public int autoSellConfirmDelayTicks = 2;
    /** Chờ GUI shop mở sau khi gõ lệnh, trước khi coi là hỏng. */
    public int autoSellGuiWaitTicks = 100;
    /** Trần thời gian cho MỘT lượt ghé shop; hết giờ thì đóng menu và gõ lệnh lại. */
    public int autoSellVisitTimeoutTicks = 200;
    /** Nghỉ sau khi gõ lệnh 3 lần mà GUI vẫn không mở. */
    public int autoSellGuiFailCooldownTicks = 200;
    /** Chờ sau khi vào lại server rồi mới gõ lệnh bán (server cần chút thời gian nạp dữ liệu). */
    public int autoSellResumeDelayTicks = 60;

    // ------------------------------------------------------------------
    // Server restart — "chờ hết restart rồi tự bật lại"
    // ------------------------------------------------------------------

    /** Nhận diện server đang khởi động lại (đọc chat + lệnh không ăn) rồi tạm dừng. */
    public boolean restartDetectEnabled = true;
    /** Thấy dấu hiệu restart thì nằm im ngần này giây. */
    public int restartWaitSeconds = 60;
    /**
     * Hết thời gian chờ thì cứ ngần này giây thử gõ lệnh một lần cho tới khi shop mở lại.
     *
     * <p>60s vì một lần restart thường mất 5–10 phút: mỗi phút thử một lần là đủ bám sát lúc server
     * quay lại mà không spam lệnh vào server đang khởi động ("cứ mỗi phút test bật gui một lần").
     */
    public int restartProbeSeconds = 60;
    /** Từ khoá trong chat/hệ thống coi là "server sắp/đang restart" (phân cách bằng dấu phẩy). */
    public String restartKeywords =
            "restart,restarting,reboot,rebooting,khởi động lại,khoi dong lai,bảo trì,bao tri,maintenance";

    // ------------------------------------------------------------------
    // Auto Reconnect — tốc độ + vào lại khi bị văng
    // ------------------------------------------------------------------

    /** Bị kick/mất kết nối (không phải tự bấm thoát) thì tự vào lại. */
    public boolean reconnectOnKick = true;
    /** Chờ ngần này giây rồi mới vào lại sau khi bị văng. */
    public int reconnectKickDelaySeconds = 10;
    /** Khoảng cách giữa các bước của màn thoát server (tắt auto → đóng shop → mở ESC). */
    public int reconnectStepTicks = 10;
    /** Giữ menu ESC ngần này tick trước khi bấm "Rời khỏi máy chủ". */
    public int reconnectMenuTicks = 20;
    /** Một lần kết nối được phép treo ở màn "Connecting" bao lâu (giây) trước khi tính là hỏng. */
    public int reconnectConnectTimeoutSeconds = 20;
    /** Vào lại hỏng thì thử lại sau ngần này giây (lặp mãi tới khi vào được). */
    public int reconnectRetrySeconds = 10;

    // --- Player Alert (cảnh báo người chơi lại gần) ---
    public boolean playerAlertEnabled = false;
    public int playerAlertRange = 100;
    /** Disconnect the moment a stranger is spotted. */
    public boolean playerAlertLogout = true;
    /** Beep straight through the speakers, ignoring Minecraft's volume. */
    public boolean playerAlertSound = true;
    public String playerAlertWebhook = "";
    public int playerAlertHotkey = -1;
    /** Names that never trigger the alarm. */
    public List<String> friends = new ArrayList<>();

    // --- Auto Pay ---
    public boolean payEnabled = false;
    public String payTarget = "";
    public double payThreshold = 0.0;
    public double payAmount = 1_000_000.0;
    public boolean payAllAboveKeep = false;
    public double payKeep = 0.0;
    public int payCheckIntervalSec = 5;
    public int payCooldownSec = 5;
    public boolean payReadFromScreen = true;
    public String payBalanceCommand = "bal";
    public String payCommand = "pay %target% %amount%";
    public boolean payRoundAmount = true;
    public double payMaxPerPayment = 0.0;
    public int payHotkey = -1;
    public boolean payChatLog = true;

    // --- Auto Reconnect ---
    public boolean reconnectEnabled = false;
    public double reconnectHours = 2.0;
    /**
     * Gap between leaving and rejoining, so the reconnect doesn't look instant/bot-like.
     *
     * <p>Kept small on purpose: this is dead time staring at the main menu, and a human who wants back
     * on a server does it in a few seconds, not eight. Still long enough that the server finishes tearing
     * down the old session before the new join arrives (an instant rejoin is what trips "already logged
     * in" errors and looks scripted).
     */
    public int reconnectDelaySeconds = 2;

    // --- GUI open key ---
    public int openGuiKey = -1;

    /** LEGACY: vị trí panel bản cũ — bỏ qua từ 2026-08-18 để layout lưới mới hiện một lần. */
    @Deprecated
    public String guiPanelsMain = "";
    /** Vị trí các panel của màn Auto Bán (ClickGUI kéo-thả), dạng "x,y|x,y|...". */
    public String guiPanelsMain2 = "";

    // --- No-Render + Staff List + Auto Sign ---
    /**
     * No-Render — MỘT nút gộp: bật là ẩn TẤT CẢ vật phẩm rơi + toàn bộ terrain, đồ/block mới xuất
     * hiện cũng không bao giờ được vẽ (mixin cancel theo từng frame). Block vẫn còn va chạm, đồ vẫn
     * nhặt được, block entity (rương, spawner...) vẫn vẽ.
     */
    public boolean noRenderEnabled = false;
    /** Hiện HUD "Staff Online" khi có staff trong tab-list. */
    public boolean staffHudEnabled = true;
    /** Vị trí thẻ HUD staff; -1 = tự neo góc phải-trên. Kéo thả trong menu. */
    public int staffHudX = -1;
    public int staffHudY = -1;
    /** Vị trí thẻ "Farm Stats"; -1 = góc trên-trái như cũ. Kéo thả trong menu Auto Bán. */
    public int statsHudX = -1;
    public int statsHudY = -1;
    /** Auto Sign: có staff ĐỨNG GẦN (≤ {@link #staffRadius} block) → đóng băng auto, đứng im, đặt bảng.
     * Staff chỉ online trong server (không tới gần) KHÔNG kích hoạt. */
    public boolean autoSignEnabled = false;
    /** Bán kính (block) coi là "staff tới gần" cho Auto Sign. Mặc định 10. */
    public int staffRadius = 10;
    /** Tối đa 4 dòng cách nhau bằng "|", mỗi dòng ≤15 ký tự. */
    public String signText = "Minh AFK ban hang|Khong dung hack|Cam on staff <3";
    public java.util.List<String> staffNames = new ArrayList<>(java.util.Arrays.asList(
            "DrDonutt", "Dough4", "Fallerfly", "Evxn", "Ryuui", "Shyalyy", "OGsummer",
            "ItsDefRealMe", "LzouZMp5", "Munkerlich", "Chaon", "Showered", "PastaGamer",
            "Bautiegar", "bloodspulse", "GsMusie", "Frwost", "FluffyMaster07", "W1zoX_",
            "Itszdeath", "archivePedro", "evify", "NoahvdAa", "Zababi", "Nathan", "Owen1212055"));

    /** @return true nếu tên chưa có (so không phân biệt hoa thường). */
    public boolean addStaff(String name)
    {
        if (name == null || name.isBlank())
        {
            return false;
        }
        for (String s : this.staffNames)
        {
            if (s != null && s.equalsIgnoreCase(name.trim()))
            {
                return false;
            }
        }
        this.staffNames.add(name.trim());
        return true;
    }

    public void removeStaff(String name)
    {
        this.staffNames.removeIf(s -> s != null && s.equalsIgnoreCase(name));
    }

    public void resetStaff()
    {
        this.staffNames = new AutoSellVdmConfig().staffNames;
    }


    public static AutoSellVdmConfig get()
    {
        if (INSTANCE == null)
        {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static AutoSellVdmConfig load()
    {
        if (Files.exists(CONFIG_PATH))
        {
            try
            {
                AutoSellVdmConfig c = GSON.fromJson(Files.readString(CONFIG_PATH), AutoSellVdmConfig.class);
                if (c != null)
                {
                    if (c.autoSellCommand == null)     { c.autoSellCommand = "sell"; }
                    if (c.autoSellWebhook == null)     { c.autoSellWebhook = ""; }
                    if (c.autoSellReportMarks == null) { c.autoSellReportMarks = "1,15,30,60"; }
                    if (c.autoSellWebhookUserId == null) { c.autoSellWebhookUserId = ""; }
                    if (c.autoSellWebhookName == null) { c.autoSellWebhookName = "Auto Bán"; }
                    if (c.autoSellWebhookAvatar == null) { c.autoSellWebhookAvatar = ""; }
                    if (c.autoSellDelay < 1)           { c.autoSellDelay = 1; }
                    if (c.autoSellAreaSlots < 1)       { c.autoSellAreaSlots = 45; }
                    if (c.autoSellValuePerSale < 0)    { c.autoSellValuePerSale = 0; }
                    if (c.autoSellItems == null)       { c.autoSellItems = new ArrayList<>(); }
                    if (c.playerAlertWebhook == null)  { c.playerAlertWebhook = ""; }
                    if (c.playerAlertRange < 1)        { c.playerAlertRange = 100; }
                    if (c.friends == null)             { c.friends = new ArrayList<>(); }
                    if (c.payTarget == null)           { c.payTarget = ""; }
                    if (c.payBalanceCommand == null)   { c.payBalanceCommand = "bal"; }
                    if (c.payCommand == null)          { c.payCommand = "pay %target% %amount%"; }
                    if (c.payCheckIntervalSec < 1)     { c.payCheckIntervalSec = 1; }
                    if (c.payCooldownSec < 1)          { c.payCooldownSec = 1; }
                    if (c.payThreshold < 0)            { c.payThreshold = 0; }
                    if (c.payAmount < 0)               { c.payAmount = 0; }
                    if (c.payKeep < 0)                 { c.payKeep = 0; }
                    if (c.reconnectHours <= 0)         { c.reconnectHours = 2.0; }
                    if (c.reconnectDelaySeconds < 1)   { c.reconnectDelaySeconds = 2; }
                    if (c.guiPanelsMain == null)       { c.guiPanelsMain = ""; }
                    if (c.signText == null)            { c.signText = "Minh AFK ban hang|Khong dung hack|Cam on staff <3"; }
                    if (c.staffNames == null || c.staffNames.isEmpty()) { c.staffNames = new AutoSellVdmConfig().staffNames; }
                    c.clampNewFields();
                    return c;
                }
            }
            catch (Exception ignored) { }
        }
        AutoSellVdmConfig c = new AutoSellVdmConfig();
        c.save();
        return c;
    }

    public void save()
    {
        try
        {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        }
        catch (IOException ignored) { }
    }

    /**
     * Kéo mọi ô tốc độ/nhịp về khoảng dùng được.
     *
     * <p>Chạy cả khi đọc file cũ (Gson để nguyên giá trị 0 cho field mới chưa có trong json — 0 tick
     * nghĩa là "làm mỗi tick", còn 0 phút thì sẽ chèn nhét lẻ liên tục) lẫn khi menu vừa lưu một ô bỏ
     * trống. Một chỗ duy nhất, nên không có đường nào lọt qua với số vô lý.
     */
    public void clampNewFields()
    {
        // Nâng cấp MỘT LẦN theo lời chốt 2026-08-18 (nhét lẻ 5–10 stack, đợt 3 phút sau mỗi 15 phút):
        // file cũ chưa có rhythmVersion nên đọc ra 0 → đổi mặc định đúng một lần rồi ghi mốc lại;
        // user chỉnh tay về sau không bao giờ bị đè nữa.
        if (this.rhythmVersion < 2)
        {
            this.rhythmVersion = 2;
            this.sellRandomDumpMinutes = 15;
            this.sellRandomDumpMinStacks = 5;
            this.sellRandomDumpMaxStacks = 10;
            this.sellRandomWindowMinutes = 3;
        }

        this.sellPhase1Percent = clamp(this.sellPhase1Percent, 1, 100, 100);
        this.sellPhase2Percent = clamp(this.sellPhase2Percent, 1, 100, 50);
        this.sellRandomDumpMinutes = clamp(this.sellRandomDumpMinutes, 0, 720, 15);
        this.sellRandomDumpJitterSeconds = clamp(this.sellRandomDumpJitterSeconds, 0, 3600, 90);
        this.sellRandomDumpMinStacks = clamp(this.sellRandomDumpMinStacks, 1, 36, 5);
        this.sellRandomDumpMaxStacks = clamp(this.sellRandomDumpMaxStacks, 1, 36, 10);

        if (this.sellRandomDumpMaxStacks < this.sellRandomDumpMinStacks)
        {
            this.sellRandomDumpMaxStacks = this.sellRandomDumpMinStacks;
        }

        this.sellRandomHoldSeconds = clamp(this.sellRandomHoldSeconds, 0, 3600, 60);
        this.sellRandomWindowMinutes = clamp(this.sellRandomWindowMinutes, 0, 60, 3);

        this.autoSellPollTicks = clamp(this.autoSellPollTicks, 1, 200, 1);
        this.autoSellConfirmDelayTicks = clamp(this.autoSellConfirmDelayTicks, 0, 100, 2);
        this.autoSellGuiWaitTicks = clamp(this.autoSellGuiWaitTicks, 20, 600, 100);
        this.autoSellVisitTimeoutTicks = clamp(this.autoSellVisitTimeoutTicks, 40, 2400, 200);
        this.autoSellGuiFailCooldownTicks = clamp(this.autoSellGuiFailCooldownTicks, 20, 2400, 200);
        this.autoSellResumeDelayTicks = clamp(this.autoSellResumeDelayTicks, 0, 600, 60);

        this.restartWaitSeconds = clamp(this.restartWaitSeconds, 5, 3600, 60);
        this.restartProbeSeconds = clamp(this.restartProbeSeconds, 5, 1800, 60);

        if (this.restartKeywords == null || this.restartKeywords.isBlank())
        {
            this.restartKeywords = "restart,restarting,reboot,rebooting,khởi động lại,khoi dong lai,"
                    + "bảo trì,bao tri,maintenance";
        }

        this.reconnectKickDelaySeconds = clamp(this.reconnectKickDelaySeconds, 3, 600, 10);
        this.reconnectStepTicks = clamp(this.reconnectStepTicks, 1, 200, 10);
        this.reconnectMenuTicks = clamp(this.reconnectMenuTicks, 1, 400, 20);
        this.reconnectConnectTimeoutSeconds = clamp(this.reconnectConnectTimeoutSeconds, 5, 300, 20);
        this.reconnectRetrySeconds = clamp(this.reconnectRetrySeconds, 3, 600, 10);
    }

    /** {@code value} nếu nằm trong [min,max]; 0/âm (field mới trong file cũ) trả về {@code fallback}. */
    private static int clamp(int value, int min, int max, int fallback)
    {
        if (value <= 0 && min > 0)
        {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Is everything needed for a payment filled in? */
    public boolean isPayConfigured()
    {
        return this.payTarget != null && this.payTarget.isBlank() == false
                && this.payCommand != null && this.payCommand.isBlank() == false
                && (this.payAllAboveKeep || this.payAmount > 0);
    }

    public boolean isFriend(String name)
    {
        if (name == null || this.friends == null)
        {
            return false;
        }
        for (String f : this.friends)
        {
            if (f != null && f.equalsIgnoreCase(name))
            {
                return true;
            }
        }
        return false;
    }

    /** @return true when the name was not already listed. */
    public boolean addFriend(String name)
    {
        if (name == null || name.isBlank() || this.isFriend(name))
        {
            return false;
        }
        if (this.friends == null)
        {
            this.friends = new ArrayList<>();
        }
        this.friends.add(name.trim());
        this.save();
        return true;
    }

    public void removeFriend(String name)
    {
        if (this.friends != null)
        {
            this.friends.removeIf(f -> f != null && f.equalsIgnoreCase(name));
            this.save();
        }
    }

    public void clearFriends()
    {
        this.friends = new ArrayList<>();
        this.save();
    }
}
