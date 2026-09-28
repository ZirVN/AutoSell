/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 * Selling logic ported from the Litematica fork's autosell.AutoSell (itself from the
 * "ryantiarno" addon's AutoSell module).
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Auto Sell: types the shop command, waits for the GUI, shovels the bag onto the counter in two loads
 * and clicks the confirm slot the moment the counter is full — the whole cycle runs unattended.
 *
 * <p>The old "Thủ công" mode (mod only shovels, you type the command and press sell yourself) was
 * REMOVED at the user's request: it doubled every code path here for a workflow nobody used, and its
 * half-automatic state — armed, but only acting while a shop GUI happened to be open — was the source of
 * most of the "why is it not selling" confusion.
 *
 * <p>Which stacks match is decided by the whitelist/blacklist mode plus the {@code autoSellItems} list.
 * Earnings are counted by {@link SellStats} and pushed to Discord by {@link AutoSellWebhook}.
 */
public class AutoSell
{
    private static final AutoSell INSTANCE = new AutoSell();

    /** Player inventory size = how many trailing slots of a container menu belong to the player. */
    private static final int PLAYER_SLOTS = 36;
    private static final int MAX_GUI_RETRIES = 3;
    /** Passes with items in hand but nothing moving, before the sell area counts as full. */
    private static final int FULL_DETECT_PASSES = 2;
    private static final int MAX_DEAD_CONFIRMS = 4;

    // Every wait below used to be a hard-coded constant. They are now knobs in the config (menu
    // "⏱ Tốc độ") because a shop's pace is server-specific: what is comfortable on one server is
    // either too slow (dead AFK time) or too fast (the shop can't keep up) on another.

    /** ~5s for the shop GUI to open. */
    private static int guiWaitTicks()      { return AutoSellVdmConfig.get().autoSellGuiWaitTicks; }
    private static int confirmDelayTicks() { return AutoSellVdmConfig.get().autoSellConfirmDelayTicks; }
    /** How often the bag is re-checked while parked. 1 = the instant it fills. */
    private static int idlePollTicks()     { return Math.max(1, AutoSellVdmConfig.get().autoSellPollTicks); }
    private static int guiFailCooldown()   { return AutoSellVdmConfig.get().autoSellGuiFailCooldownTicks; }

    /**
     * Hard ceiling on one shop visit, in ticks (20 ticks = 1s; 200 = 10 seconds by default).
     *
     * <p>Everything between "the shop GUI opened" and "the sale went through" is covered by this one
     * timer. Without it a shop that stops responding — a lagging server, a confirm button that silently
     * stopped working, a GUI that stayed open but went inert — leaves the state machine ping-ponging
     * between MOVING and CONFIRM forever, with the menu stuck open and nothing being sold. On timeout
     * the menu is closed and the whole cycle restarts from {@code /sell}, which is what a player would
     * do by hand.
     */
    private static int visitTimeoutTicks() { return AutoSellVdmConfig.get().autoSellVisitTimeoutTicks; }

    /**
     * Wait this long after an automatic reconnect before sending the shop command (3 seconds).
     *
     * <p>A server usually needs a moment after the join packet before it will accept commands — plugins
     * are still loading the player's data, and a {@code /sell} fired in that window is often silently
     * dropped. Waiting costs nothing while AFK and avoids burning the retry budget.
     */
    private static int resumeDelayTicks()  { return AutoSellVdmConfig.get().autoSellResumeDelayTicks; }

    /** Jitter for the "nhét lẻ" schedule. Not security-sensitive — only there to break up a fixed rhythm. */
    private static final java.util.Random RANDOM = new java.util.Random();

    private int delayTicks;

    // --- auto-mode state (unused in manual mode) ---
    private State state = State.IDLE;
    private int guiWaitTicks;
    private int guiRetries;
    private int failedPasses;
    private int deadConfirms;
    private int lastContainerId = -1;
    private int usedBeforeConfirm;
    /** Những loại item chính MOD đã nhét lên quầy lượt này. Mọi phép đo "đã bán chưa" dựa vào đây
     *  chứ KHÔNG đếm ô không-rỗng nữa: shop có kính viền trang trí thì số ô luôn > 0 nên phép đo cũ
     *  bao giờ cũng sai (bán được vẫn bị chấm là "bấm không ăn"). */
    private final Set<Item> dumpedItems = new HashSet<>();
    /** Số ô túi đầy trước khi bấm nút — tín hiệu bán thứ 2 (shop bán thẳng từ túi người chơi). */
    private int bagBeforeConfirm;
    /** Doanh thu tai luc bam ban — tien tang sau do la bang chung ban THANH CONG
     *  (shop kieu nuot-do-ngay lam phep so o truoc/sau tro nen mu). */
    private double revenueBeforeConfirm;
    /** Ô nút vừa bấm; -1 = KHÔNG tìm thấy nút nào (không được tính là "bấm không ăn"). */
    private int lastConfirmSlot = -1;
    /** containerId của menu shop thật — chặn việc nhầm inventory/rương của chính người chơi. */
    private int shopContainerId = -1;
    /** Vật phẩm đã nhét lượt này; chỉ cộng vào thống kê KHI bán thành công. */
    private int visitItemsPending;
    /** Ticks left in the current shop visit; 0 = no visit in flight. See {@link #VISIT_TIMEOUT_TICKS}. */
    private int visitTicks;

    /**
     * Which half of the two-load rhythm we are on.
     *
     * <p>The shop grid holds 45 slots, the bag holds 36: one full bag fills 36 of them, so a second
     * load is always needed before the counter is full. That second load only has 9 slots to fill, so
     * waiting for another FULL bag would leave the player standing around with a shop half loaded —
     * hence {@link Phase#TWO} opens the gate at half a bag and the sale fires the moment the grid fills.
     */
    private enum Phase
    {
        /** Load 1: wait for a completely full bag, dump it (36 of the 45 slots). */
        ONE,
        /** Load 2: half a bag is enough — it tops the grid up and the confirm click follows at once. */
        TWO
    }

    private Phase phase = Phase.ONE;

    /** When the next "nhét lẻ" WINDOW opens, in ms. 0 = not scheduled (feature off). */
    private long nextRandomDumpMs;
    /** The current partial-drop window runs until this (ms); 0 = normal two-load rhythm. */
    private long randomWindowUntilMs;
    /** Rest between two partial drops INSIDE the window. Never blocks the normal rhythm. */
    private long randomHoldUntilMs;
    /** The visit in flight is a partial drop: dump {@link #randomVisitBudget} stacks, confirm, close. */
    private boolean randomVisit;
    private int randomVisitBudget;
    private int randomVisitMoved;

    /** While this is in the future the seller is idle because the server looks like it is restarting. */
    private long restartUntilMs;
    /** After the first restart wait, later waits use the shorter probe interval instead. */
    private boolean restartProbing;

    /** Item list resolved from config, rebuilt whenever the raw strings change. */
    private Set<Item> resolvedItems = null;
    private List<String> resolvedFrom = null;

    public static AutoSell getInstance()
    {
        return INSTANCE;
    }

    public boolean isEnabled()
    {
        return AutoSellVdmConfig.get().autoSellEnabled;
    }

    public void toggle()
    {
        this.setEnabled(this.isEnabled() == false);
    }

    public void setEnabled(boolean on)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.autoSellEnabled = on;
        cfg.save();

        this.delayTicks = 0;
        this.guiRetries = 0;
        this.failedPasses = 0;
        this.deadConfirms = 0;
        this.lastContainerId = -1;
        this.visitTicks = 0;
        this.phase = Phase.ONE;
        this.randomHoldUntilMs = 0L;
        this.randomWindowUntilMs = 0L;
        this.randomVisit = false;
        this.restartUntilMs = 0L;
        this.restartProbing = false;

        if (on == false)
        {
            this.state = State.IDLE;
            msg("§cAuto Sell: TẮT");
            return;
        }

        this.resetReportSchedule();
        this.scheduleRandomDump();

        // Always enter through WAITING_ITEMS, whose full-bag check is the gate for the whole cycle.
        // Jumping straight to MOVING when a shop is already open would dump a half-empty bag.
        this.park(net.minecraft.client.Minecraft.getInstance());
        msg("§aAuto Sell: BẬT — đầy túi (36/36) mới mở /" + command() + " đổ lượt 1");
    }

    /** Whitelist = sell ONLY the listed items; blacklist = sell everything except them. */
    public String modeName()
    {
        return AutoSellVdmConfig.get().autoSellWhitelist ? "Whitelist" : "Blacklist";
    }

    /** Armed but idle: waiting for the bag to reach this load's mark. */
    public boolean isWaitingForItems()
    {
        return this.isEnabled()
                && (this.state == State.WAITING_ITEMS || this.state == State.SEND_CMD);
    }

    public void onClientTick(Minecraft mc)
    {
        if (mc.player == null || mc.gameMode == null)
        {
            return;
        }

        // The Discord report is about the stats, not about selling — keep it running even while the
        // seller itself is off, otherwise turning Auto Sell off silently kills the reports too.
        this.tickWebhook();

        // Staff online + Auto Sign armed → FREEZE toàn bộ máy bán tới khi staff off hết.
        if (StaffGuard.isFrozen())
        {
            return;
        }

        if (this.isEnabled() == false)
        {
            return;
        }

        if (this.delayTicks > 0)
        {
            this.delayTicks--;
            return;
        }

        this.tickAuto(mc);
    }

    // ------------------------------------------------------------------
    // The cycle — type the command, load the counter, press confirm
    // ------------------------------------------------------------------

    private void tickAuto(Minecraft mc)
    {
        if (mc.player.connection == null)
        {
            return;
        }

        // Server restarting: sit still. Firing commands into a server that is shutting down (or into the
        // limbo/queue it drops you in) is exactly what left the mod stuck "searching" for a shop GUI that
        // was never going to open, burning its retry budget in the process.
        if (this.isRestartWaiting())
        {
            this.delayTicks = 20;
            return;
        }

        // The 15' + 3' clock: open/close the partial-drop window regardless of what state we're in.
        this.tickRhythmWindow();

        // A visit is "in flight" from the moment /sell is sent until the sale is confirmed. Counting here
        // rather than inside each handler means no state can escape the timeout by never being reached.
        if (this.visitTicks > 0 && --this.visitTicks == 0)
        {
            this.abortVisit(mc, "Quá " + (visitTimeoutTicks() / 20) + " giây không bán được — thoát menu và /"
                    + command() + " lại");
            return;
        }

        switch (this.state)
        {
            case SEND_CMD      -> this.handleSend(mc);
            case WAITING_GUI   -> this.handleWaitGui(mc);
            case WAITING_ITEMS -> this.handleWaitItems(mc);
            case MOVING        -> this.handleMoving(mc);
            case CONFIRM       -> this.handleConfirm(mc);
            case AFTER_SELL    -> this.handleAfterSell(mc);
            // Armed in the config but never started this session (saved ON, or a reconnect mid-run):
            // pick the run back up rather than sitting dead.
            case IDLE          -> this.state = State.SEND_CMD;
        }
    }

    /**
     * Give up on this shop visit: close the (possibly inert) menu and go straight back to sending the
     * command, so the next attempt starts from a clean GUI instead of reusing the stuck one.
     */
    private void abortVisit(Minecraft mc, String why)
    {
        this.visitTicks = 0;
        this.failedPasses = 0;
        this.deadConfirms = 0;
        this.guiRetries = 0;
        // KHONG dong GUI /sell nua (loi chot cua user: "bat /sell la ko bao gio tat gui"). Menu cu
        // van dung do; may ban chi reset trang thai roi thu lai. Chi Auto Reconnect (out/vao lai) va
        // server restart moi duoc phep lam menu bien mat.
        // Menu còn mở → thử lại NGAY trong đó. Chỉ khi server đã đóng menu mới gõ lệnh mở lại (gõ
        // lệnh lúc menu còn mở = server đóng container cũ = "mod làm tắt GUI").
        if (this.isShopScreen(mc))
        {
            this.state = State.MOVING;
            this.visitTicks = visitTimeoutTicks();
        }
        else
        {
            this.state = State.SEND_CMD;
        }
        this.delayTicks = confirmDelayTicks();
        msg("§e" + why);
    }

    private void handleSend(Minecraft mc)
    {
        // "Đầy kho mới mở /sell": the bag must have reached this load's mark before a shop trip is worth
        // making — except inside the partial-drop window, where a handful of stacks (min..max) plus the
        // between-drop rest is the whole gate. This is the single choke point every entry into the sell
        // cycle passes through — arming the feature while already standing in a shop, and reconnecting
        // mid-run, both land here rather than selling immediately.
        if (this.readyForVisit(mc) == false || this.isHolding())
        {
            this.park(mc);
            return;
        }

        if (this.isShopScreen(mc))
        {
            // Menu /sell vẫn mở → nhét thẳng vào. Gõ lệnh lại sẽ khiến SERVER đóng container cũ.
            this.beginVisitKind();
            this.visitTicks = visitTimeoutTicks();
            this.state = State.MOVING;
            this.delayTicks = 0;
            return;
        }

        this.beginVisitKind();
        this.lastContainerId = mc.screen instanceof AbstractContainerScreen<?> screen
                ? screen.getMenu().containerId : -1;
        mc.player.connection.sendCommand(command()); // no leading slash
        this.guiWaitTicks = guiWaitTicks();
        this.state = State.WAITING_GUI;
    }

    private void handleWaitGui(Minecraft mc)
    {
        if (mc.screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().containerId != this.lastContainerId)
        {
            this.shopContainerId = screen.getMenu().containerId;
            this.guiRetries = 0;
            this.delayTicks = configDelay();
            // The shop is open — the visit clock starts here. A shop that answers also means the server
            // is back up, so any restart backoff is cleared.
            this.visitTicks = visitTimeoutTicks();
            this.restartProbing = false;
            this.state = State.MOVING;
            return;
        }

        if (--this.guiWaitTicks <= 0)
        {
            if (++this.guiRetries >= MAX_GUI_RETRIES)
            {
                // Shop didn't open — wrong command, or the server is lagging. Back off and retry later
                // rather than switching a hotkey-armed feature off behind the user's back.
                this.guiRetries = 0;
                // Khong dong GUI: neu co menu nao do dang mo thi cu de nguyen, chi lui lai va thu lai.
                this.park(mc);

                // Three commands in a row with no GUI is also exactly what a restarting server looks
                // like from the client's side: you are still "connected", chat works, but commands go
                // nowhere. Treat it as a restart and wait it out on the probe schedule instead of
                // hammering the same dead command every few seconds.
                if (AutoSellVdmConfig.get().restartDetectEnabled)
                {
                    this.enterRestartWait("Lệnh /" + command() + " không phản hồi");
                }
                else
                {
                    this.delayTicks = guiFailCooldown();
                    msg("§cKhông mở được GUI /" + command() + " — chờ rồi thử lại");
                }
            }
            else
            {
                this.state = State.SEND_CMD;
            }
        }
    }

    /**
     * Chờ mốc kế tiếp. Menu /sell thường VẪN MỞ (park không đóng): đủ mốc thì nhét thẳng vào, khỏi gõ
     * lệnh. Chỉ khi server tự đóng menu (mc.screen == null) mới gõ /sell lại để mở.
     */
    private void handleWaitItems(Minecraft mc)
    {
        this.delayTicks = idlePollTicks();

        // Waiting out a server restart → do nothing at all.
        if (this.isHolding())
        {
            return;
        }

        // Hold off until the PLAYER'S BAG has reached this load's mark ("đầy kho mới bán") — never sell
        // early just because the shop's own counter happens to fill up first.
        if (this.readyForVisit(mc) == false)
        {
            return;
        }

        this.failedPasses = 0;
        this.guiRetries = 0;

        if (isShopScreen(mc))
        {
            // Menu /sell vẫn mở (trường hợp thường): nhét lượt kế tiếp thẳng vào, không gõ lệnh lại.
            this.beginVisitKind();
            this.visitTicks = visitTimeoutTicks();
            this.state = State.MOVING;
            this.delayTicks = 0;
        }
        else if (mc.screen == null)
        {
            this.state = State.SEND_CMD;
            this.delayTicks = 0;
        }
        // Any other screen is the player's own doing — leave it alone, poll again next second.
    }

    /**
     * The one gate both entry points share. Normal rhythm: sellable items in the bag AND the bag at this
     * load's mark (100% / 2 rows). Partial-drop window: min..max stacks gathered and the between-drop
     * rest over — the load marks deliberately do NOT apply ("ko cần phải đầy theo 2 quy tắc kia").
     */
    private boolean readyForVisit(Minecraft mc)
    {
        if (this.inRandomWindow())
        {
            return this.randomLoadReady(mc) && this.isRandomResting() == false;
        }

        return this.hasSellableItems(mc) && this.isLoadReady(mc);
    }

    /** Pin down what kind of visit is being opened, and roll the stack count for a partial drop. */
    private void beginVisitKind()
    {
        this.randomVisit = this.inRandomWindow();
        this.randomVisitBudget = this.randomVisit ? randomDumpStacks() : 0;
        this.randomVisitMoved = 0;
        this.dumpedItems.clear();
        this.visitItemsPending = 0;
        this.lastConfirmSlot = -1;
    }

    private void handleMoving(Minecraft mc)
    {
        if (isShopScreen(mc) == false)
        {
            this.park(mc);
            return;
        }

        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        int containerSlots = menu.slots.size() - PLAYER_SLOTS;
        int sellArea = sellAreaSize(containerSlots);

        // Partial drop: put EXACTLY the rolled 5–10 stacks on the counter and press confirm right away —
        // no waiting for a full grid, and selling at once keeps the counter empty so the two-load math
        // ("2 lượt = đầy kho → xác nhận") stays exact for the normal rhythm.
        if (this.randomVisit)
        {
            int moved = this.dumpInventory(mc, menu, containerSlots, sellArea, this.randomVisitBudget);
            this.randomVisitBudget -= moved;
            this.randomVisitMoved += moved;

            if (moved > 0 || this.soldSlots(menu, sellArea) > 0)
            {
                this.failedPasses = 0;
                this.delayTicks = confirmDelayTicks();
                this.state = State.CONFIRM;
            }
            else
            {
                // Nothing went in and the counter is bare (shop refused it / bag emptied under us) —
                // rest and let the window try again (menu vẫn mở).
                this.finishRandomVisit(mc);
            }
            return;
        }

        // Nhét cả túi vào ô trống của kho trong một tick (shift-click, server tự xếp vào ô trống —
        // không đụng nút kính lime). KHÔNG còn dựa vào "grid đầy 45 ô": kho server có nút + pane
        // trang trí nên đếm ô không đáng tin; trigger bán giờ theo NHỊP TÚI + lúc kho hết chỗ.
        // Đo bằng TÚI NGƯỜI CHƠI, không đếm số click: client tự đoán quick-move nên "moved" vẫn > 0
        // dù shop từ chối nhận đồ → vòng MOVING quay mãi, không bao giờ tới bước bấm nút.
        int bagBefore = filledSlots(mc);
        int moved = this.dumpInventory(mc, menu, containerSlots, sellArea, Integer.MAX_VALUE);
        int landed = bagBefore - filledSlots(mc);

        if (landed > 0)
        {
            this.failedPasses = 0;
            this.delayTicks = configDelay();
            return; // còn nhét được — tiếp
        }

        // Không nhét thêm được nữa (moved == 0): hoặc kho HẾT CHỖ, hoặc túi HẾT đồ bán.
        boolean bagHasItems = this.hasSellableItems(mc);

        if (bagHasItems)
        {
            // Túi còn đồ mà không vào được kho → kho đầy (hoặc shop lag). Đợi vài nhịp phòng lag,
            // rồi BẤM NÚT bán cả kho ngay ("bán cả kho một lần") — khỏi chờ hết nhịp.
            if (++this.failedPasses < FULL_DETECT_PASSES)
            {
                this.delayTicks = configDelay();
                return;
            }
            this.failedPasses = 0;
            this.state = State.CONFIRM;
            return;
        }

        // Túi đã hết đồ cho lượt này.
        this.failedPasses = 0;

        if (this.phase == Phase.ONE && AutoSellVdmConfig.get().sellPhaseEnabled)
        {
            // Lượt 1 xong nhưng kho chưa đầy: GIỮ MENU MỞ, chờ farm cho đủ 2 tầng rồi đổ lượt 2.
            this.advancePhase();
            this.park(mc);
            msg("§7Lượt 1 xong: cả túi đã lên kho — giữ nguyên menu /" + command()
                    + ", chờ đủ 2 tầng (18 ô) đổ lượt 2 rồi bấm nút bán");
            return;
        }

        // Lượt 2 đã đổ xong (hoặc nhịp 2-lượt tắt) → bấm nút kính lime bán CẢ KHO một lần.
        this.state = State.CONFIRM;
    }

    /**
     * Quick-move matching stacks from the player's half of the menu into the shop's half, in a single
     * tick. Returns how many stacks were clicked.
     *
     * @param maxStacks ceiling on how many stacks may go in this pass — {@code Integer.MAX_VALUE} for a
     *                  normal load, a small number for the periodic partial drop.
     */
    private int dumpInventory(Minecraft mc, AbstractContainerMenu menu, int containerSlots, int sellArea,
            int maxStacks)
    {
        // Đếm ô TRỐNG trực tiếp: công thức cũ (sellArea − ô không rỗng) bị kính trang trí ăn hết
        // budget; shop trang trí nhiều thì budget = 0 → không nhét gì mà vẫn đi bấm nút.
        int budget = Math.min(maxStacks, freeSlots(menu, sellArea));
        int moved = 0;

        for (int i = containerSlots; i < menu.slots.size() && moved < budget; i++)
        {
            ItemStack stack = menu.getSlot(i).getItem();

            if (this.shouldSell(stack) == false)
            {
                continue;
            }

            int count = stack.getCount();
            mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
            moved++;
            this.dumpedItems.add(stack.getItem());
            // Thống kê chỉ cộng KHI bán thành công: client tự đoán quick-move nên click vẫn "thành
            // công" ngay cả lúc shop từ chối → cộng ngay ở đây sẽ thổi phồng số liệu.
            this.visitItemsPending += count;
        }

        return moved;
    }

    private void handleConfirm(Minecraft mc)
    {
        if (isShopScreen(mc) == false)
        {
            this.park(mc);
            return;
        }

        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        int containerSlots = menu.slots.size() - PLAYER_SLOTS;

        this.usedBeforeConfirm = this.soldSlots(menu, sellAreaSize(containerSlots));
        this.bagBeforeConfirm = filledSlots(mc);
        this.revenueBeforeConfirm = SellStats.getTotalEarned();

        // Nút bán = ô KÍNH LIME (lime_stained_glass) — tự dò trong kho rồi bấm, khỏi phụ thuộc số ô
        // cứng (server này nút không nằm ô 53). Không dò ra thì mới dùng "Ô xác nhận" trong config.
        int confirmSlot = findConfirmButton(menu, containerSlots);
        if (confirmSlot < 0)
        {
            confirmSlot = AutoSellVdmConfig.get().autoSellConfirmSlot;
        }

        // -1 (or out of range) = this shop has no confirm button; moving the item IS the sale.
        if (confirmSlot >= 0 && confirmSlot < containerSlots)
        {
            this.lastConfirmSlot = confirmSlot;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, confirmSlot, 0, ClickType.PICKUP, mc.player);
        }
        else
        {
            // KHÔNG bấm gì cả → không được tính là "bấm không ăn" (báo cũ đánh lừa người dùng).
            this.lastConfirmSlot = -1;
            msg("§cKhông thấy nút bán trong kho " + containerSlots + " ô — sửa §fÔ xác nhận§c trong menu");
        }

        // Chờ server trả lời rồi mới đo (2 tick ~100ms thường nhỏ hơn ping → kết quả như tung xu).
        this.delayTicks = Math.max(10, confirmDelayTicks());
        this.state = State.AFTER_SELL;
    }

    /**
     * Tìm ô "nút bán" trong nửa kho của shop: ưu tiên ô có KÍNH LIME (lime_stained_glass /
     * lime_stained_glass_pane) đúng như user chỉ; không có thì bắt tạm ô xanh-lá/emerald khác, cuối
     * cùng là ô có tên chứa "sell/confirm/xác nhận". Trả -1 nếu không thấy (rơi về config confirmSlot).
     */
    private static int findConfirmButton(AbstractContainerMenu menu, int containerSlots)
    {
        // Quet HAI VONG: vong 1 chi xet cac o NGOAI luoi ban (hang nut/vien trang tri), vong 2 moi mo
        // ra ca kho. Ly do: neu chinh mon dang ban LA lime_stained_glass (hoac kinh mau khac) thi vong
        // quet ca kho se bam nham vao O DO — tuc la RUT DO RA thay vi bam Ban. Nut that gan nhu luon
        // nam ngoai 45 o ban, nen uu tien vung do la an toan tuyet doi.
        int sellArea = sellAreaSize(containerSlots);
        int found = scanConfirmButton(menu, sellArea, containerSlots);

        return found >= 0 ? found : scanConfirmButton(menu, 0, containerSlots);
    }

    /** Tim nut ban trong khoang slot [from, to): kinh LIME truoc, roi o xanh la, roi theo TEN nut. */
    private static int scanConfirmButton(AbstractContainerMenu menu, int from, int to)
    {
        int greenFallback = -1;
        int paneFallback = -1;
        int nameFallback = -1;

        for (int i = Math.max(0, from); i < to; i++)
        {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty())
            {
                continue;
            }

            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

            // ĐÚNG khối kính lime = nút bán → nhận ngay. KHÔNG dùng contains: "lime_stained_glass"
            // cũng khớp "lime_stained_glass_pane" (kính viền trang trí) nên bản cũ bấm nhầm viền.
            if (id.equals("lime_stained_glass"))
            {
                return i;
            }
            if (paneFallback < 0 && id.equals("lime_stained_glass_pane"))
            {
                paneFallback = i;
            }
            if (greenFallback < 0 && (id.contains("green") || id.contains("emerald")))
            {
                greenFallback = i;
            }
            if (nameFallback < 0)
            {
                String name = stripAccents(stack.getHoverName().getString().toLowerCase());
                if (name.contains("sell") || name.contains("confirm") || name.contains("xac nhan")
                        || name.contains("dong y") || name.contains("ban tat ca") || name.contains("ban het"))
                {
                    nameFallback = i;
                }
            }
        }

        // Tên nút ("Bán tất cả"/"Confirm") đáng tin hơn "một ô nào đó màu xanh", nên xếp trên.
        if (nameFallback >= 0)
        {
            return nameFallback;
        }
        return paneFallback >= 0 ? paneFallback : greenFallback;
    }

    /** Bỏ dấu tiếng Việt để so tên nút ("xác nhận" → "xac nhan"). */
    private static String stripAccents(String s)
    {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace("đ", "d");
    }

    private void handleAfterSell(Minecraft mc)
    {
        if (isShopScreen(mc) == false)
        {
            this.park(mc); // some shops close on the sale
            return;
        }

        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        int containerSlots = menu.slots.size() - PLAYER_SLOTS;

        // Resync the client's view of the slots.
        mc.gameMode.handleInventoryMouseClick(menu.containerId, -999, 0, ClickType.PICKUP, mc.player);

        // Ve thu ba: TIEN VE = ban thanh cong. Shop nuot-do-ngay (khay ban luon
        // trong) lam hai phep so o mu tit — chinh la ly do Items dung 0 trong khi
        // Money/Sells van chay (user chup duoc).
        boolean sold = this.soldSlots(menu, sellAreaSize(containerSlots)) < this.usedBeforeConfirm
                || filledSlots(mc) < this.bagBeforeConfirm
                || SellStats.getTotalEarned() > this.revenueBeforeConfirm;

        if (sold)
        {
            this.deadConfirms = 0; // the sale went through
            if (this.visitItemsPending > 0)
            {
                this.countSale(this.visitItemsPending);
                this.visitItemsPending = 0;
            }
            this.dumpedItems.clear();
            // Progress was made, so this visit earns a fresh clock rather than being killed mid-way
            // through emptying a very full bag.
            this.visitTicks = visitTimeoutTicks();

            // A partial drop just sold its handful — close up and rest until the window's next drop.
            if (this.randomVisit)
            {
                this.finishRandomVisit(mc);
                return;
            }

            // The counter has just been emptied, so the rhythm starts over from a full bag. Parking here
            // is what keeps it a rhythm at all: without it the leftovers from load 2 would go straight
            // back onto the counter and the mod would drip-feed the shop forever.
            if (AutoSellVdmConfig.get().sellPhaseEnabled)
            {
                this.phase = Phase.ONE;
                this.park(mc);
                return;
            }
        }
        else if (this.lastConfirmSlot >= 0 && ++this.deadConfirms > MAX_DEAD_CONFIRMS)
        {
            // Không TẮT máy bán nữa: shop lag nhìn y hệt ô xác nhận sai, và tự tắt là thứ làm farm
            // đứng cả đêm ("xong lại ngưng bán"). Đóng menu, gõ lại lệnh, bán tiếp — sai thật thì
            // cảnh báo lặp lại và người chơi sửa số ô khi nhìn thấy.
            this.deadConfirms = 0;
            this.abortVisit(mc, "Bấm ô xác nhận không ăn — mở lại /" + command()
                    + " bán tiếp (nếu lặp mãi thì sửa §fÔ xác nhận§e)");
            return;
        }

        this.delayTicks = confirmDelayTicks();
        this.state = State.MOVING;
    }

    /**
     * Wait for the next mark WITHOUT closing the shop. Đúng ý user: "trong khi bật /sell là lặp lại
     * nhét vật phẩm theo cách lượt 1 - 2, ko phải tắt đi bật lại" — mở /sell MỘT lần rồi cứ để menu
     * đó, farm rơi đồ về túi thì nhét tiếp theo nhịp; chỉ gõ lệnh lại nếu server tự đóng menu.
     */
    private void park(Minecraft mc)
    {
        this.failedPasses = 0;
        this.deadConfirms = 0;
        // Parking means "waiting for the farm to refill the bag", which is unbounded by design — the
        // visit clock only covers active selling, so stop it here or it would fire while idling.
        this.visitTicks = 0;
        this.randomVisit = false;

        // KHÔNG đóng menu: giữ nguyên GUI /sell đang mở để lượt sau nhét thẳng vào, khỏi gõ lệnh lại.
        this.state = State.WAITING_ITEMS;
        this.delayTicks = idlePollTicks();
    }

    private void closeShop(Minecraft mc)
    {
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen)
        {
            mc.player.closeContainer();
        }
    }

    /** Reset in-flight state on a world join so nothing carries across a reconnect. */
    public static void onJoin()
    {
        INSTANCE.delayTicks = 0;
        INSTANCE.guiRetries = 0;
        INSTANCE.failedPasses = 0;
        INSTANCE.deadConfirms = 0;
        INSTANCE.lastContainerId = -1;
        INSTANCE.visitTicks = 0;
        // A fresh world = a fresh rhythm: the shop counter is whatever the server says it is, and the
        // safe assumption is an empty one, i.e. start again from a full bag.
        INSTANCE.phase = Phase.ONE;
        INSTANCE.randomHoldUntilMs = 0L;
        INSTANCE.randomWindowUntilMs = 0L;
        INSTANCE.randomVisit = false;
        INSTANCE.restartUntilMs = 0L;
        INSTANCE.restartProbing = false;
        INSTANCE.scheduleRandomDump();
        INSTANCE.state = INSTANCE.isEnabled() ? State.SEND_CMD : State.IDLE;
        INSTANCE.resetReportSchedule();
    }

    /**
     * Resume selling right after an automatic reconnect, without waiting for the bag to fill again.
     *
     * <p>Auto Reconnect exists so an AFK farm can run overnight; if the mod came back and then sat idle
     * until the next full bag, the minutes right after each reconnect would be dead time. So the sell
     * cycle is kicked off immediately — {@code handleSend} still refuses to open a shop for a bag that
     * isn't full, so this only ever *starts* the machine, it can't sell a half load.
     */
    public static void onReconnected()
    {
        if (INSTANCE.isEnabled() == false)
        {
            return;
        }

        onJoin();

        INSTANCE.state = State.SEND_CMD;
        INSTANCE.delayTicks = resumeDelayTicks();
        msg("§aĐã vào lại server — tiếp tục tự bán.");
    }

    /** Disconnect: drop the in-flight sell sequence, the config flag itself is untouched. */
    public static void pause()
    {
        INSTANCE.state = State.IDLE;
        INSTANCE.delayTicks = 0;
        INSTANCE.guiRetries = 0;
        INSTANCE.failedPasses = 0;
        INSTANCE.deadConfirms = 0;
        INSTANCE.lastContainerId = -1;
        INSTANCE.visitTicks = 0;
    }

    // ------------------------------------------------------------------
    // Nhịp bán: load 1 (full bag) → load 2 (half bag) → sale, plus the periodic partial drop
    // ------------------------------------------------------------------

    /** How many of the player's 36 slots hold something. */
    public static int filledSlots(Minecraft mc)
    {
        if (mc.player == null)
        {
            return 0;
        }

        int used = 0;

        for (int i = 0; i < PLAYER_SLOTS; i++)
        {
            if (mc.player.getInventory().getItem(i).isEmpty() == false)
            {
                used++;
            }
        }

        return used;
    }

    /** The fill mark this load waits for: 100% of the bag on load 1, half a bag on load 2. */
    public int loadTargetPercent()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.sellPhaseEnabled == false)
        {
            return cfg.sellPhase1Percent;
        }

        return this.phase == Phase.ONE ? cfg.sellPhase1Percent : cfg.sellPhase2Percent;
    }

    /** Has the bag reached this load's mark? 100% still means every single slot, as before. */
    private boolean isLoadReady(Minecraft mc)
    {
        int target = this.loadTargetPercent();

        if (target >= 100)
        {
            return isInventoryFull(mc);
        }

        // Round UP, so "50% of 36" is 18 slots and not 17 — the mark is a floor, never a fraction short.
        int needed = Math.max(1, (PLAYER_SLOTS * target + 99) / 100);
        return filledSlots(mc) >= needed;
    }

    /** Which load we are on, for the menu/HUD ("nhịp 1" / "nhịp 2"). */
    public int loadNumber()
    {
        return AutoSellVdmConfig.get().sellPhaseEnabled && this.phase == Phase.TWO ? 2 : 1;
    }

    /**
     * A load has landed on the counter → everything after it is a "top-up" load, i.e. load 2, until the
     * sale resets the rhythm in {@link #handleAfterSell}. Deliberately NOT a toggle: if the counter is
     * still not full after load 2 (a shop with a bigger grid, or items the shop refused), the next load
     * must keep the half-bag mark rather than flipping back to demanding a full one.
     */
    private void advancePhase()
    {
        this.phase = AutoSellVdmConfig.get().sellPhaseEnabled ? Phase.TWO : Phase.ONE;
    }

    /** Schedule the next partial drop: the configured gap, ± the jitter. 0 phút = feature off. */
    private void scheduleRandomDump()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.sellRandomDumpMinutes <= 0 || cfg.sellRandomWindowMinutes <= 0)
        {
            this.nextRandomDumpMs = 0L;
            return;
        }

        long base = cfg.sellRandomDumpMinutes * 60_000L;
        long jitter = cfg.sellRandomDumpJitterSeconds * 1000L;
        long offset = jitter <= 0 ? 0L : RANDOM.nextLong(-jitter, jitter + 1);

        this.nextRandomDumpMs = System.currentTimeMillis() + Math.max(5_000L, base + offset);
    }

    /**
     * The 15' + 3' clock ("sau 15 phút bán theo quy tắc 2 lần thì phút thứ 16–18... randomtick"):
     * after {@code sellRandomDumpMinutes} of normal two-load selling a window opens for
     * {@code sellRandomWindowMinutes}; inside it every gathered handful of 5–10 stacks gets its own
     * quick visit (dump → confirm → close, see {@link #finishRandomVisit}); when the window closes the
     * normal rhythm resumes and the next window is lined up — repeating forever.
     */
    private void tickRhythmWindow()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        long now = System.currentTimeMillis();

        if (this.randomWindowUntilMs != 0L)
        {
            if (now >= this.randomWindowUntilMs)
            {
                this.randomWindowUntilMs = 0L;
                this.randomHoldUntilMs = 0L;
                this.scheduleRandomDump();
                msg("§7Hết đợt nhét lẻ — về nhịp cũ: đầy túi đổ lượt 1, đủ 2 tầng đổ lượt 2 rồi bán");
            }
            return;
        }

        if (this.nextRandomDumpMs != 0L && now >= this.nextRandomDumpMs
                && cfg.sellRandomWindowMinutes > 0)
        {
            this.randomWindowUntilMs = now + cfg.sellRandomWindowMinutes * 60_000L;
            this.randomHoldUntilMs = 0L;
            this.nextRandomDumpMs = 0L; // hẹn lại khi đợt này đóng — chu kỳ tính từ LÚC HẾT đợt
            msg("§7Tới đợt nhét lẻ " + cfg.sellRandomWindowMinutes + " phút: thấy đủ đồ là nhét "
                    + cfg.sellRandomDumpMinStacks + "–" + cfg.sellRandomDumpMaxStacks
                    + " stack rồi bán luôn (menu /" + command() + " vẫn mở)");
        }
    }

    private boolean inRandomWindow()
    {
        return this.randomWindowUntilMs != 0L && System.currentTimeMillis() < this.randomWindowUntilMs;
    }

    /** Between two drops of the same window — the only thing this rest ever blocks is the next drop. */
    private boolean isRandomResting()
    {
        return this.randomHoldUntilMs != 0L && System.currentTimeMillis() < this.randomHoldUntilMs;
    }

    /** Enough loose stacks for one partial drop? ("một lần nhét là 5st-10st" → need at least min.) */
    private boolean randomLoadReady(Minecraft mc)
    {
        int need = Math.max(1, AutoSellVdmConfig.get().sellRandomDumpMinStacks);
        int have = 0;

        for (int i = 0; i < PLAYER_SLOTS; i++)
        {
            if (this.shouldSell(mc.player.getInventory().getItem(i)) && ++have >= need)
            {
                return true;
            }
        }

        return false;
    }

    /** How many stacks this partial drop pushes in — somewhere in the configured 5..10 band. */
    private static int randomDumpStacks()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        int min = Math.max(1, cfg.sellRandomDumpMinStacks);
        int max = Math.max(min, cfg.sellRandomDumpMaxStacks);

        return min == max ? min : RANDOM.nextInt(min, max + 1);
    }

    /**
     * One partial drop is over (sold, or nothing to sell): rest the configured seconds (menu VẪN MỞ),
     * and — if the window is still open — the next handful gets its own visit. The counter was left
     * empty either way, so the two-load rhythm restarts from a full bag whenever the window closes.
     */
    private void finishRandomVisit(Minecraft mc)
    {
        int moved = this.randomVisitMoved;
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        this.phase = Phase.ONE;
        this.randomHoldUntilMs = System.currentTimeMillis() + cfg.sellRandomHoldSeconds * 1000L;
        this.park(mc);

        if (moved > 0)
        {
            msg("§7Nhét lẻ §f" + moved + "§7 stack và bán luôn — nghỉ " + cfg.sellRandomHoldSeconds
                    + "s (đợt còn " + this.windowSecondsLeft() + "s)");
        }
    }

    /** Seconds left of the current partial-drop window, for chat/menu. 0 = not in a window. */
    public int windowSecondsLeft()
    {
        long left = this.randomWindowUntilMs - System.currentTimeMillis();
        return left <= 0L ? 0 : (int) ((left + 999L) / 1000L);
    }

    /** Waiting out a server restart: don't touch the shop. (The partial-drop rest is handled separately
     *  by {@link #isRandomResting} and never blocks the normal rhythm.) */
    private boolean isHolding()
    {
        return this.isRestartWaiting();
    }

    /** Seconds left of the current rest / restart wait, for the menu. 0 = not waiting. */
    public int holdSecondsLeft()
    {
        long until = Math.max(this.randomHoldUntilMs, this.restartUntilMs);
        long left = until - System.currentTimeMillis();

        return left <= 0L ? 0 : (int) ((left + 999L) / 1000L);
    }

    // ------------------------------------------------------------------
    // Server restart
    // ------------------------------------------------------------------

    private boolean isRestartWaiting()
    {
        return this.restartUntilMs != 0L && System.currentTimeMillis() < this.restartUntilMs;
    }

    public boolean isWaitingForRestart()
    {
        return this.isRestartWaiting();
    }

    /**
     * Stop selling for a while because the server is (or looks like it is) restarting.
     *
     * <p>The first wait is the long one from the config — a restart takes a minute or so and there is
     * nothing to gain from poking at it. Every wait after that is the shorter probe interval, so once
     * the server is nearly back the mod tries a command every N seconds instead of every minute. A shop
     * GUI that finally opens clears {@code restartProbing} and the run is back to normal.
     */
    private void enterRestartWait(String why)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        int seconds = this.restartProbing ? cfg.restartProbeSeconds : cfg.restartWaitSeconds;

        this.restartUntilMs = System.currentTimeMillis() + seconds * 1000L;
        this.restartProbing = true;
        this.visitTicks = 0;
        this.guiRetries = 0;
        this.failedPasses = 0;
        this.park(net.minecraft.client.Minecraft.getInstance());

        msg("§e" + why + " — server có vẻ đang restart, chờ " + seconds + "s rồi thử lại");
    }

    /**
     * Chat/system line from the server. A restart announcement ("server restarting in 10 seconds",
     * "bảo trì"...) parks the seller BEFORE the kick, so nothing is mid-transaction when the server goes
     * down and no command is fired into a server that is already shutting down.
     */
    public static void onServerText(String text)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.restartDetectEnabled == false || cfg.autoSellEnabled == false
                || text == null || text.isEmpty() || INSTANCE.isRestartWaiting())
        {
            return;
        }

        String lower = text.toLowerCase();

        for (String word : cfg.restartKeywords.split(","))
        {
            String needle = word.trim().toLowerCase();

            if (needle.isEmpty() == false && lower.contains(needle))
            {
                // Straight to the long wait: a restart notice means the server IS going down, so this is
                // not a probe situation.
                INSTANCE.restartProbing = false;
                INSTANCE.enterRestartWait("Server báo restart");
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private void countSale(int count)
    {
        SellStats.recordItemsSold(count);

        if (AutoSellVdmConfig.get().autoSellChatParsing == false)
        {
            SellStats.recordSale(AutoSellVdmConfig.get().autoSellValuePerSale);
        }
    }

    private static int configDelay()
    {
        return Math.max(1, AutoSellVdmConfig.get().autoSellDelay);
    }

    /** Does this stack match the configured mode + list? */
    private boolean shouldSell(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }

        return this.items().contains(stack.getItem()) == AutoSellVdmConfig.get().autoSellWhitelist;
    }

    /** Anything in the player's own inventory the mode says to sell? */
    private boolean hasSellableItems(Minecraft mc)
    {
        for (int i = 0; i < PLAYER_SLOTS; i++)
        {
            if (this.shouldSell(mc.player.getInventory().getItem(i)))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Is the bag packed 100%? EVERY slot (main inventory + hotbar) must hold something —
     * "khi 100% full inventory mới nhét vào kho". This used to tolerate one free slot, which meant
     * batches could start on a 35/36 bag; the batches are now strictly full-bag.
     */
    public static boolean isInventoryFull(Minecraft mc)
    {
        if (mc.player == null)
        {
            return false;
        }

        for (int i = 0; i < PLAYER_SLOTS; i++)
        {
            if (mc.player.getInventory().getItem(i).isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    /** Free slots in the player's own inventory, for the menu's status line. */
    public static int freeInventorySlots()
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
        {
            return 0;
        }

        int free = 0;

        for (int i = 0; i < PLAYER_SLOTS; i++)
        {
            if (mc.player.getInventory().getItem(i).isEmpty())
            {
                free++;
            }
        }

        return free;
    }

    /** True if a container screen with slots beyond the player's own 36 is open (i.e. a shop). */
    private boolean isShopScreen(Minecraft mc)
    {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen))
        {
            return false;
        }
        AbstractContainerMenu menu = screen.getMenu();

        // Túi đồ CỦA CHÍNH NGƯỜI CHƠI cũng có > 36 ô (46: chế tạo + giáp + tay phụ) — bản cũ nhầm nó
        // là shop, nên bấm E lúc đang chờ là mod shift-click đồ vào ô chế tạo/giáp rồi "bấm xác nhận"
        // vào một trong những ô đó. Loại thẳng InventoryMenu, và khi đã biết menu shop thật thì bắt
        // buộc đúng containerId đó (rương người chơi tự mở cũng không còn bị nhầm).
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu)
        {
            return false;
        }
        if (menu.slots.size() <= PLAYER_SLOTS)
        {
            return false;
        }
        return this.shopContainerId < 0 || menu.containerId == this.shopContainerId;
    }

    /** Số ô trong lưới bán đang chứa ĐÚNG loại đồ mod vừa nhét (bỏ qua kính/nút trang trí). */
    private int soldSlots(AbstractContainerMenu menu, int sellArea)
    {
        int n = 0;

        for (int i = 0; i < sellArea; i++)
        {
            ItemStack stack = menu.getSlot(i).getItem();

            if (stack.isEmpty() == false && this.dumpedItems.contains(stack.getItem()))
            {
                n++;
            }
        }
        return n;
    }

    /** Số ô TRỐNG thật sự của lưới bán (đếm trực tiếp, không trừ theo ô không-rỗng). */
    private static int freeSlots(AbstractContainerMenu menu, int sellArea)
    {
        int free = 0;

        for (int i = 0; i < sellArea; i++)
        {
            if (menu.getSlot(i).getItem().isEmpty())
            {
                free++;
            }
        }
        return free;
    }

    /** Occupied slots in the shop's own half of the menu. */
    private static int usedSlots(AbstractContainerMenu menu, int containerSlots)
    {
        int used = 0;

        for (int i = 0; i < containerSlots; i++)
        {
            if (menu.getSlot(i).getItem().isEmpty() == false)
            {
                used++;
            }
        }

        return used;
    }

    /**
     * How many leading slots of the shop GUI are the actual sell grid (rows 1-5 = 45 by default). The
     * bottom row of most shop GUIs is decoration — sort buttons, the green confirm square, filler panes —
     * which never accepts items, so counting it would mean "full" could never be reached.
     */
    private static int sellAreaSize(int containerSlots)
    {
        return Math.min(AutoSellVdmConfig.get().autoSellAreaSlots, containerSlots);
    }

    /** The shop command to send, without the leading slash. */
    private static String command()
    {
        String raw = AutoSellVdmConfig.get().autoSellCommand;
        raw = raw == null ? "" : raw.trim();

        while (raw.startsWith("/"))
        {
            raw = raw.substring(1).trim();
        }

        return raw.isEmpty() ? "sell" : raw;
    }

    // ------------------------------------------------------------------
    // Discord report schedule: driven by the configured minute marks
    // ------------------------------------------------------------------

    private long cycleStartMs;
    private int reportStage;

    /** The configured marks ("1,15,30,60"), parsed and sorted; falls back to a single 60-minute mark. */
    private static int[] reportSchedule()
    {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        String raw = AutoSellVdmConfig.get().autoSellReportMarks;

        for (String part : (raw == null ? "" : raw).split(","))
        {
            try
            {
                int v = Integer.parseInt(part.trim());

                if (v > 0)
                {
                    set.add(v);
                }
            }
            catch (NumberFormatException ignored) { }
        }

        if (set.isEmpty())
        {
            set.add(60);
        }

        return set.stream().mapToInt(Integer::intValue).toArray();
    }

    private void tickWebhook()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.autoSellWebhookEnabled == false || cfg.autoSellWebhook == null
                || cfg.autoSellWebhook.isBlank())
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (this.cycleStartMs == 0L)
        {
            this.cycleStartMs = now;
            this.reportStage = 0;
            return;
        }

        int[] schedule = reportSchedule();

        // The marks can change while running (edited in the menu) — never index past the new array.
        if (this.reportStage >= schedule.length)
        {
            this.reportStage = 0;
            this.cycleStartMs = now;
            return;
        }

        if (now - this.cycleStartMs < schedule[this.reportStage] * 60_000L)
        {
            return;
        }

        AutoSellWebhook.sendReport(schedule[this.reportStage], schedule.length);
        this.reportStage++;

        if (this.reportStage >= schedule.length)
        {
            this.reportStage = 0;
            this.cycleStartMs = now; // start the next cycle from this report
        }
    }

    /** Minutes until the next scheduled report, for the menu label. -1 when reporting is off. */
    public int minutesToNextReport()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        if (cfg.autoSellWebhookEnabled == false || cfg.autoSellWebhook == null
                || cfg.autoSellWebhook.isBlank() || this.cycleStartMs == 0L)
        {
            return -1;
        }

        int[] schedule = reportSchedule();
        int stage = Math.min(this.reportStage, schedule.length - 1);
        long remaining = schedule[stage] * 60_000L - (System.currentTimeMillis() - this.cycleStartMs);

        return (int) Math.max(0L, (remaining + 59_999L) / 60_000L);
    }

    /** Restart the schedule, so turning reporting on gives a report at the first mark. */
    public void resetReportSchedule()
    {
        this.cycleStartMs = System.currentTimeMillis();
        this.reportStage = 0;
    }

    // ------------------------------------------------------------------
    // Item list
    // ------------------------------------------------------------------

    /** The configured item list as a Set, rebuilt only when the config strings actually change. */
    private Set<Item> items()
    {
        List<String> ids = AutoSellVdmConfig.get().autoSellItems;

        if (this.resolvedItems == null || this.resolvedFrom == null || this.resolvedFrom.equals(ids) == false)
        {
            Set<Item> out = new HashSet<>();

            for (String id : ids)
            {
                try
                {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id.trim()));

                    if (item != null)
                    {
                        out.add(item);
                    }
                }
                catch (Exception ignored) { }
            }

            this.resolvedItems = out;
            this.resolvedFrom = List.copyOf(ids);
        }

        return this.resolvedItems;
    }

    /** The listed items, resolved to Item objects — for the selection menu. */
    public static List<Item> listedItems()
    {
        return new ArrayList<>(INSTANCE.items());
    }

    public static boolean isListed(Item item)
    {
        return INSTANCE.items().contains(item);
    }

    /** Add / remove an item from the sell list. */
    public static void setListed(Item item, boolean listed)
    {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);

        if (key == null)
        {
            return;
        }

        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        List<String> ids = new ArrayList<>(cfg.autoSellItems);
        ids.remove(key.toString());

        if (listed)
        {
            ids.add(key.toString());
        }

        cfg.autoSellItems = ids;
        cfg.save();
    }

    public static void clearListed()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.autoSellItems = new ArrayList<>();
        cfg.save();
    }

    private static void msg(String text)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null)
        {
            mc.player.displayClientMessage(Component.literal("§7[§6AutoSell§7] §f" + text), true);
        }
    }

    private enum State
    {
        IDLE,
        SEND_CMD,
        WAITING_GUI,
        /** Armed with the shop open, waiting for the inventory to refill. */
        WAITING_ITEMS,
        MOVING,
        CONFIRM,
        AFTER_SELL
    }
}
