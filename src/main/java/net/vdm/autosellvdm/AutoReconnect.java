/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Auto Reconnect: while enabled, after the player has been on a multiplayer server for
 * {@code reconnectHours}, it disconnects and immediately re-joins that same server. Off by default;
 * the interval (default 2h) is set in the GUI. The timer starts on each world join and is cleared
 * when leaving, so the countdown only ever measures continuous time on one server.
 */
public final class AutoReconnect
{
    private static final AutoReconnect INSTANCE = new AutoReconnect();

    /**
     * The steps a logout is broken into, in order. A real player doesn't vanish in one frame: they stop
     * the macro, close the shop, press ESC, read the menu for a moment, then click "Rời khỏi máy chủ".
     * Doing all of that in a single tick is the most bot-like thing this feature can do, so each step
     * gets its own tick range with a pause in between (see {@link #stepTicks}).
     */
    private enum LeaveStep
    {
        /** Auto Sell + stats already switched off; let the last click settle before touching the GUI. */
        STOPPED,
        /** Shop container closed — the same packet pressing ESC on a chest sends. */
        SHOP_CLOSED,
        /** The pause menu is up on screen, as if ESC had been pressed; next step leaves the server. */
        MENU_OPEN
    }

    /** Ticks between two logout steps — about half a second by default, i.e. unhurried human pacing. */
    private static int stepTicks()
    {
        return Math.max(1, AutoSellVdmConfig.get().reconnectStepTicks);
    }

    /** The phases of getting back in: sit on the server list → connection in flight. */
    private enum JoinStep
    {
        /** On the server list the disconnect landed on, waiting out the "chờ 3s" gap. */
        WAIT,
        /** The connection was started; waiting to see whether the join actually lands. */
        CONNECTING
    }

    /** How long a started connection may sit on the connect screen before it counts as failed. */
    private static long connectTimeoutMs()
    {
        return Math.max(5, AutoSellVdmConfig.get().reconnectConnectTimeoutSeconds) * 1000L;
    }

    /** Pause between failed join attempts (connection throttle, kick, server restarting...). */
    private static long retryDelayMs()
    {
        return Math.max(3, AutoSellVdmConfig.get().reconnectRetrySeconds) * 1000L;
    }

    /**
     * Never rejoin sooner than this after leaving, whatever the config says — the user-specified
     * human pace is "chờ 3s rồi ấn vào server". If the server still throttles the reconnect
     * ("Connection throttled!"), the retry loop in {@link #finishRejoin} covers it: the next attempt
     * fires one retry interval later instead of the player being stranded outside.
     */
    private static final int MIN_REJOIN_DELAY_SECONDS = 3;

    /** Which half of the rejoin we're in, or null when no rejoin is pending. */
    private JoinStep joinStep;

    /** How long the pause menu stays up before the disconnect, so the menu is actually visible. */
    private static int menuTicks()
    {
        return Math.max(1, AutoSellVdmConfig.get().reconnectMenuTicks);
    }

    /** Which logout step we are on, or null when no logout is in progress. */
    private LeaveStep step;
    /** Ticks left before advancing past {@link #step}. */
    private int stepTicks;

    /** The server we joined, captured on join so we can reconnect to exactly it. */
    private ServerData currentServer;
    /**
     * The last multiplayer server we were actually on, kept even after leaving.
     *
     * <p>{@link #currentServer} is cleared the moment the world goes away, which is right for the timer
     * but useless for the "we got kicked, go back in" path — by the time the disconnect screen is up
     * there would be nothing left to reconnect TO. This one survives.
     */
    private ServerData lastServer;
    /** When the current session started (ms). 0 = not on a server / not counting. */
    private long sessionStartMs;
    /** Set while we're driving a reconnect, so the next join doesn't think it's a fresh manual join. */
    private boolean reconnecting;
    /**
     * When to actually send the join, in {@code System.currentTimeMillis()}. 0 = nothing pending.
     *
     * <p>Rejoining in the same tick we disconnected is the single most bot-like thing this feature can
     * do: no human closes a server and is back in under 50 ms. Splitting it into "leave now, join at
     * {@code rejoinAtMs}" makes the gap look like someone clicking through the menu, and also gives the
     * server time to actually tear the old session down — some plugins reject a join that races their
     * own quit handling ("already logged in").
     */
    private long rejoinAtMs;
    /** The server the pending join is for; kept separate so a manual disconnect can cancel cleanly. */
    private ServerData pendingServer;
    /** Was Auto Sell on before we logged off? Only then is it switched back on after the rejoin. */
    private boolean resumeSell;
    /** Same for the stats counter, so a player who had it off doesn't find it on again. */
    private boolean resumeStats;

    private AutoReconnect() {}

    public static AutoReconnect getInstance()
    {
        return INSTANCE;
    }

    /** Called on every world/server join: (re)start the countdown and remember the server. */
    public static void onJoin()
    {
        Minecraft mc = Minecraft.getInstance();
        boolean wasAutoReconnect = INSTANCE.reconnecting;

        // Any join settles a pending rejoin — either it WAS ours (we just arrived), or the player picked
        // a server themselves during the wait, in which case yanking them back out would be wrong. The
        // same goes for a half-finished logout: being in a world again means it is no longer ours to
        // finish, and letting it run would disconnect the player from the server they just chose.
        INSTANCE.rejoinAtMs = 0L;
        INSTANCE.pendingServer = null;
        INSTANCE.joinStep = null;
        INSTANCE.step = null;
        INSTANCE.stepTicks = 0;

        INSTANCE.currentServer = mc.getCurrentServer();
        INSTANCE.sessionStartMs = INSTANCE.currentServer != null ? System.currentTimeMillis() : 0L;
        INSTANCE.reconnecting = false;

        if (INSTANCE.currentServer != null)
        {
            INSTANCE.lastServer = INSTANCE.currentServer;
        }

        // This join is the tail end of our own reconnect, not the player picking a server. Restore what
        // doReconnect switched off, then hand back to AutoSell so an overnight farm starts selling again
        // instead of idling until the next full bag.
        if (wasAutoReconnect)
        {
            AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

            if (INSTANCE.resumeStats)
            {
                cfg.autoSellStats = true;
            }
            // setEnabled saves the config itself; call it last so both flags land in one write.
            if (INSTANCE.resumeSell)
            {
                cfg.save();
                AutoSell.getInstance().setEnabled(true);
            }
            else
            {
                cfg.save();
            }

            INSTANCE.resumeSell = false;
            INSTANCE.resumeStats = false;

            AutoSell.onReconnected();
        }
    }

    /** Called on disconnect: stop counting (a manual disconnect must not trigger anything). */
    public static void onLeave()
    {
        if (INSTANCE.reconnecting == false)
        {
            INSTANCE.sessionStartMs = 0L;
            INSTANCE.currentServer = null;
        }
    }

    public String statusText()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        if (this.step != null)
        {
            return switch (this.step)
            {
                case STOPPED     -> "đang tắt auto...";
                case SHOP_CLOSED -> "đang mở menu ESC...";
                case MENU_OPEN   -> "đang rời server...";
            };
        }
        if (this.rejoinAtMs != 0L)
        {
            if (this.joinStep == JoinStep.CONNECTING)
            {
                return "đang kết nối...";
            }
            long left = (this.rejoinAtMs - System.currentTimeMillis() + 999L) / 1000L;
            return "vào lại sau " + (left < 0 ? 0 : left) + "s";
        }
        if (cfg.reconnectEnabled == false)
        {
            return "OFF";
        }
        if (this.sessionStartMs == 0L)
        {
            return "chờ vào server";
        }
        long leftMs = (long) (cfg.reconnectHours * 3_600_000.0) - (System.currentTimeMillis() - this.sessionStartMs);
        if (leftMs < 0) leftMs = 0;
        long secs = leftMs / 1000L;
        return String.format("còn %02d:%02d:%02d", secs / 3600L, secs % 3600L / 60L, secs % 60L);
    }

    public void tick(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        // A logout in progress owns the client until it has left; nothing else may start meanwhile.
        if (this.step != null)
        {
            this.tickLeave(mc);
            return;
        }

        // A rejoin we already scheduled is driven here, from the title screen, where mc.level is null —
        // so this has to run BEFORE the "must be in a world" guard below.
        if (this.rejoinAtMs != 0L)
        {
            if (System.currentTimeMillis() >= this.rejoinAtMs)
            {
                this.finishRejoin(mc);
            }
            return;
        }

        if (cfg.reconnectEnabled == false)
        {
            return;
        }

        // Thrown out without asking — a restart, a kick, a timeout. DisconnectedScreen is the client's
        // own "something went wrong" screen and is NEVER shown when the player leaves on purpose (the
        // pause menu's Disconnect lands on the server list, and so does our own scripted logout), so it
        // is a reliable "this was not us" signal. Go back in and let the retry loop ride out a server
        // that is still restarting.
        if (mc.level == null && cfg.reconnectOnKick && this.lastServer != null
                && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)
        {
            this.scheduleKickRejoin();
            return;
        }
        // Only count while actually in a multiplayer world with a known server.
        if (mc.level == null || mc.player == null || this.currentServer == null || this.sessionStartMs == 0L)
        {
            return;
        }

        long elapsed = System.currentTimeMillis() - this.sessionStartMs;
        long limit = (long) (cfg.reconnectHours * 3_600_000.0);
        if (elapsed < limit)
        {
            return;
        }

        // Time's up → disconnect and reconnect to the same server.
        this.doReconnect(mc, this.currentServer, "Đã đủ giờ");
    }

    /**
     * Run the reconnect right now, ignoring the timer — the "Test" button in the menu.
     *
     * <p>Deliberately goes through the very same {@link #doReconnect} the timer uses, so a successful
     * test proves the real path works (including the {@code reconnecting} flag that makes AutoSell pick
     * selling back up on the next join, and the human-looking delay before the join). Testing a separate
     * code path would prove nothing.
     *
     * @return null on success, or a human-readable reason it could not run.
     */
    public String testNow(Minecraft mc)
    {
        if (this.step != null)
        {
            return "Đang thoát ra rồi — đợi chút.";
        }

        if (this.rejoinAtMs != 0L)
        {
            return "Đang chờ vào lại rồi — đợi chút.";
        }

        if (mc.level == null || mc.player == null)
        {
            return "Phải đang ở trong server mới test được.";
        }

        ServerData server = this.currentServer != null ? this.currentServer : mc.getCurrentServer();
        if (server == null)
        {
            return "Không phải server nhiều người chơi (single-player không vào lại được).";
        }

        this.currentServer = server;
        this.doReconnect(mc, server, "Test");
        return null;
    }

    /**
     * Phase 1, step 1: stop the automation. The rest of the logout (close shop, press ESC, click
     * "Rời khỏi máy chủ") is spread over the following ticks in {@link #tickLeave}, and the join itself
     * waits a few more seconds after that; see {@link #rejoinAtMs} for why the gap matters.
     *
     * <p>Turning Auto Sell / stats off BEFORE quitting is the part that matters for looking human. A
     * real player doesn't vanish mid-transaction: they stop what they're doing, close the shop GUI, and
     * only then disconnect. Leaving the sell loop armed while the connection drops is also what produces
     * half-finished /sell state on the server side. Both are re-armed on the way back in by
     * {@link AutoSell#onReconnected()}.
     */
    private void doReconnect(Minecraft mc, ServerData server, String why)
    {
        this.reconnecting = true;
        this.sessionStartMs = 0L;
        this.pendingServer = server;

        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        int seconds = Math.max(MIN_REJOIN_DELAY_SECONDS, cfg.reconnectDelaySeconds);

        // Remember what was running so the same things come back on after the rejoin, and nothing the
        // player had switched off gets silently switched on.
        this.resumeSell = cfg.autoSellEnabled;
        this.resumeStats = cfg.autoSellStats;

        if (mc.player != null)
        {
            mc.player.displayClientMessage(
                    Component.literal("§7[§6AutoReconnect§7] §e" + why + " — tắt auto, thoát ra, "
                            + seconds + "s nữa vào lại..."), false);
        }

        // Stop the sell loop first. Everything after this is paced out over the next ticks.
        if (this.resumeSell)
        {
            AutoSell.getInstance().setEnabled(false);
        }
        if (this.resumeStats)
        {
            cfg.autoSellStats = false;
        }
        cfg.save();

        this.step = LeaveStep.STOPPED;
        this.stepTicks = stepTicks();
    }

    /**
     * Drives the remaining logout steps, one per {@link #stepTicks()} window, so the sequence looks like
     * someone working through the menu rather than a single scripted frame.
     */
    private void tickLeave(Minecraft mc)
    {
        // The world can vanish under us mid-sequence — a kick, a crash, or the player clicking "Rời khỏi
        // máy chủ" before we got there. There is nothing left to close or press ESC on, so skip straight
        // to scheduling the join; otherwise the remaining steps would no-op and never reach it.
        if (mc.level == null)
        {
            this.scheduleRejoin();
            return;
        }

        if (this.stepTicks > 0)
        {
            this.stepTicks--;
            return;
        }

        switch (this.step)
        {
            case STOPPED ->
            {
                // Close whatever container the seller had open — the ESC press on a chest GUI.
                if (mc.player != null)
                {
                    mc.player.closeContainer();
                }
                if (mc.screen != null)
                {
                    mc.setScreen(null);
                }
                this.step = LeaveStep.SHOP_CLOSED;
                this.stepTicks = stepTicks();
            }
            case SHOP_CLOSED ->
            {
                // The actual ESC press: the pause menu, exactly what a player sees before leaving.
                mc.setScreen(new PauseScreen(true));
                this.step = LeaveStep.MENU_OPEN;
                this.stepTicks = menuTicks();
            }
            // "Rời khỏi máy chủ" — land on the SERVER LIST, exactly like the pause menu's own
            // Disconnect button does in multiplayer. The old flow went to the TITLE screen instead,
            // and custom clients (Dawn, ...) replace that with their own class — which the rejoin
            // guard didn't recognise, stranding the player on the client's main menu forever.
            // The join itself is deliberately NOT done here.
            case MENU_OPEN ->
            {
                // Close the NETWORK CHANNEL first, exactly like vanilla's Disconnect button (it goes
                // through ClientLevel.disconnect() → Connection.disconnect("quitting")). Calling only
                // mc.disconnect() tears the client down but never proactively closes the channel, so
                // the proxy (Velocity on DonutSMP) still saw the old session alive and refused the
                // rejoin 3s later with "You are already connected to this proxy!".
                if (mc.getConnection() != null)
                {
                    mc.getConnection().getConnection().disconnect(
                            Component.translatable("multiplayer.status.quitting"));
                }
                mc.disconnect(new JoinMultiplayerScreen(new TitleScreen()), false);
                this.scheduleRejoin();
            }
        }
    }

    /** Logout done: hand over to the {@link #rejoinAtMs} countdown that {@link #tick} drives. */
    private void scheduleRejoin()
    {
        this.step = null;
        this.stepTicks = 0;
        this.joinStep = JoinStep.WAIT;
        // Clamped to MIN_REJOIN_DELAY_SECONDS: rejoining faster trips Spigot/Paper's connection
        // throttle and the join is refused before the server even looks at the login.
        this.rejoinAtMs = System.currentTimeMillis()
                + Math.max(MIN_REJOIN_DELAY_SECONDS, AutoSellVdmConfig.get().reconnectDelaySeconds) * 1000L;
    }

    /**
     * Phase 2 — the user-specified human flow. The disconnect above already landed on the server
     * list, exactly like clicking the pause menu's Disconnect button; sit there for the configured
     * gap ("chờ 3s"), then click the server — and RETRY when an attempt fails.
     *
     * <p>Deliberately NO screen-type checks here: custom clients (Dawn, ...) replace the vanilla
     * title / multiplayer screens with their own classes, and the old instanceof-whitelist postponed
     * forever on those — that is exactly what stranded the player on Dawn's main menu. This machine
     * only ever runs right after OUR OWN scripted logout, so whatever screen is up belongs to this
     * journey and may be driven through.
     */
    private void finishRejoin(Minecraft mc)
    {
        ServerData server = this.pendingServer;

        if (server == null)
        {
            this.clearRejoin();
            this.reconnecting = false;
            return;
        }

        // Already back in a world: the join landed. Normally onJoin() clears all of this first; this
        // covers any path where that hook didn't run, so the machine can never fire in-game.
        if (mc.level != null)
        {
            this.clearRejoin();
            return;
        }

        // A connection attempt is in flight. Reaching this timeout while still OUTSIDE a world means
        // the attempt failed ("Connection throttled!", kicked, server restarting): schedule the next.
        if (this.joinStep == JoinStep.CONNECTING)
        {
            if (mc.screen instanceof ConnectScreen)
            {
                // Still handshaking / sitting in a login queue — give it more time.
                this.rejoinAtMs = System.currentTimeMillis() + 1000L;
                return;
            }
            this.joinStep = JoinStep.WAIT;
            this.rejoinAtMs = System.currentTimeMillis() + retryDelayMs();
            return;
        }

        // The "chờ 3s" is over — "ấn vào server donutsmp.net": start the connection. Parent is a
        // vanilla server list, so a failed join drops back to a list same as a real click would. The
        // pending state is deliberately KEPT armed: if this attempt fails, the CONNECTING branch
        // above schedules the next one; on success onJoin() clears everything.
        this.joinStep = JoinStep.CONNECTING;
        this.rejoinAtMs = System.currentTimeMillis() + connectTimeoutMs();

        try
        {
            ServerAddress address = ServerAddress.parseString(server.ip);
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()),
                    mc, address, server, false, null);
        }
        catch (Throwable t)
        {
            // Couldn't even start the attempt — retry on the normal schedule rather than giving up
            // (mc.player is null out here, so a chat message could never be seen).
            this.joinStep = JoinStep.WAIT;
            this.rejoinAtMs = System.currentTimeMillis() + retryDelayMs();
            System.out.println("[AutoSellVDM] Loi khi vao lai server (se thu lai): " + t);
        }
    }

    /**
     * We were thrown off the server: line a rejoin up without any of the scripted-logout steps (there is
     * nothing left to close — we are already outside).
     *
     * <p>Nothing was switched off on the way out, so there is nothing to restore either: {@code resumeSell}
     * / {@code resumeStats} stay false and the seller simply picks up where it left off via
     * {@link AutoSell#onReconnected()}, which the {@code reconnecting} flag below triggers on the next join.
     */
    private void scheduleKickRejoin()
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        this.reconnecting = true;
        this.sessionStartMs = 0L;
        this.pendingServer = this.lastServer;
        this.resumeSell = false;
        this.resumeStats = false;
        this.joinStep = JoinStep.WAIT;
        this.rejoinAtMs = System.currentTimeMillis()
                + Math.max(3, cfg.reconnectKickDelaySeconds) * 1000L;
    }

    /** Forget the pending join — either it just fired, or something else took over. */
    private void clearRejoin()
    {
        this.rejoinAtMs = 0L;
        this.pendingServer = null;
        this.joinStep = null;
    }
}
