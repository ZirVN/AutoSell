/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Auto Pay — sends money to a chosen player whenever the account balance rises above a threshold.
 * Ported from the Litematica fork; malilib's InfoUtils replaced by a local actionbar helper and the
 * config fields moved to {@link AutoSellVdmConfig} (pay-prefixed).
 */
public class AutoPay
{
    private static final AutoPay INSTANCE = new AutoPay();

    /** Lines that look like a balance report. */
    private static final Pattern BALANCE_LINE = Pattern.compile(
            "(số ?dư|so ?du|balance|\\bbal\\b|tiền|tien|money|\\$)", Pattern.CASE_INSENSITIVE);
    /** A number with optional thousand separators and an optional K/M/B/T suffix. */
    private static final Pattern NUMBER = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)*)\\s*([kmbt])?", Pattern.CASE_INSENSITIVE);

    /** How long a balance query may go unanswered before we simply try again. */
    private static final long REPLY_TIMEOUT_MS = 8000L;
    /** Grace period after joining a world before the first query. */
    private static final long JOIN_DELAY_MS = 5000L;

    private enum State { IDLE, WAITING_BALANCE, COOLDOWN }

    private State state = State.IDLE;
    private long nextActionMs;
    private long replyDeadlineMs;
    private double lastBalance = -1.0;
    private double paidTotal;
    private int paymentCount;

    public static AutoPay getInstance()
    {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    public static boolean isEnabled()
    {
        return AutoSellVdmConfig.get().payEnabled;
    }

    public void setEnabled(boolean value)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        cfg.payEnabled = value;
        cfg.save();

        this.state = State.IDLE;
        this.nextActionMs = System.currentTimeMillis() + (value ? 1000L : 0L);

        if (value && cfg.isPayConfigured() == false)
        {
            actionBar("§7[§6Auto Pay§7] §eBẬT — nhưng chưa cấu hình người nhận/số tiền!");
            return;
        }
        actionBar(value ? "§7[§6Auto Pay§7] §aBẬT" : "§7[§6Auto Pay§7] §cTẮT");
    }

    public void toggle()
    {
        this.setEnabled(AutoSellVdmConfig.get().payEnabled == false);
    }

    /** Called when (re)joining a world: hold off a few seconds before the first balance query. */
    public static void onJoin()
    {
        INSTANCE.state = State.IDLE;
        INSTANCE.nextActionMs = System.currentTimeMillis() + JOIN_DELAY_MS;
        INSTANCE.lastBalance = -1.0;
    }

    public double getLastBalance()  { return this.lastBalance; }
    public double getPaidTotal()    { return this.paidTotal; }
    public int getPaymentCount()    { return this.paymentCount; }

    public String statusText()
    {
        if (isEnabled() == false)
        {
            return "OFF";
        }
        return switch (this.state)
        {
            case WAITING_BALANCE -> "đang hỏi số dư";
            case COOLDOWN -> "nghỉ sau khi chuyển";
            case IDLE -> "đang chờ";
        };
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public void onClientTick(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        // The user-assignable toggle key (any key, bound in the Auto Pay menu).
        this.pollHotkey(mc, cfg);

        if (cfg.payEnabled == false || mc.player == null || mc.player.connection == null)
        {
            return;
        }

        long now = System.currentTimeMillis();

        // A query that never got an answer: just try again on the normal schedule.
        if (this.state == State.WAITING_BALANCE && now >= this.replyDeadlineMs)
        {
            this.state = State.IDLE;
            this.nextActionMs = now + cfg.payCheckIntervalSec * 1000L;
            return;
        }

        // Post-payment rest is over → resume the normal check schedule.
        if (this.state == State.COOLDOWN && now >= this.nextActionMs)
        {
            this.state = State.IDLE;
            this.nextActionMs = now + cfg.payCheckIntervalSec * 1000L;
            return;
        }

        if (this.state != State.IDLE || now < this.nextActionMs)
        {
            return;
        }
        if (cfg.isPayConfigured() == false)
        {
            this.nextActionMs = now + 10_000L; // don't spam the warning
            return;
        }

        if (cfg.payReadFromScreen)
        {
            // No command at all: read the balance straight off the sidebar / tab list. This is the
            // mode for AFK selling, where a /bal round trip would interrupt the sell loop.
            double balance = BalanceReader.read(mc);
            if (balance < 0)
            {
                this.nextActionMs = now + cfg.payCheckIntervalSec * 1000L;
                return;
            }
            this.lastBalance = balance;
            this.handleBalance(balance);
            return;
        }

        // Command mode: ask the server; the reply is handled in onChatMessage.
        mc.player.connection.sendCommand(stripSlash(cfg.payBalanceCommand));
        this.state = State.WAITING_BALANCE;
        this.replyDeadlineMs = now + REPLY_TIMEOUT_MS;
    }

    // ------------------------------------------------------------------
    // Chat parsing
    // ------------------------------------------------------------------

    /** Reads the balance out of the server's reply. Always returns false — never hides a chat line. */
    public static boolean onChatMessage(Component message)
    {
        AutoPay self = INSTANCE;
        if (AutoSellVdmConfig.get().payEnabled == false || self.state != State.WAITING_BALANCE || message == null)
        {
            return false;
        }

        String text = message.getString();
        if (text == null || text.isBlank() || BALANCE_LINE.matcher(text).find() == false)
        {
            return false;
        }

        double balance = parseBalance(text);
        if (balance < 0)
        {
            return false;
        }

        self.lastBalance = balance;
        self.state = State.IDLE;
        self.handleBalance(balance);
        return false;
    }

    /** The largest number in the line — plugin formats put the balance first or last, never mid-noise. */
    static double parseBalance(String text)
    {
        Matcher m = NUMBER.matcher(text);
        double best = -1.0;
        while (m.find())
        {
            try
            {
                double value = Double.parseDouble(m.group(1).replace(",", ""));
                String suffix = m.group(2);
                if (suffix != null)
                {
                    value *= switch (Character.toLowerCase(suffix.charAt(0)))
                    {
                        case 'k' -> 1_000.0;
                        case 'm' -> 1_000_000.0;
                        case 'b' -> 1_000_000_000.0;
                        case 't' -> 1_000_000_000_000.0;
                        default -> 1.0;
                    };
                }
                if (value > best)
                {
                    best = value;
                }
            }
            catch (NumberFormatException ignored) { }
        }
        return best;
    }

    private void handleBalance(double balance)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
        long now = System.currentTimeMillis();

        double needed = cfg.payAllAboveKeep
                ? Math.max(cfg.payThreshold, cfg.payKeep)
                : Math.max(cfg.payThreshold, cfg.payAmount);
        if (balance < needed)
        {
            this.nextActionMs = now + cfg.payCheckIntervalSec * 1000L;
            return;
        }

        double toPay = cfg.payAllAboveKeep ? balance - cfg.payKeep : cfg.payAmount;
        if (cfg.payMaxPerPayment > 0 && toPay > cfg.payMaxPerPayment)
        {
            toPay = cfg.payMaxPerPayment;
        }
        if (toPay > balance)
        {
            toPay = balance;
        }
        if (cfg.payRoundAmount)
        {
            toPay = Math.floor(toPay);
        }

        if (toPay <= 0)
        {
            this.nextActionMs = now + cfg.payCheckIntervalSec * 1000L;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null)
        {
            return;
        }

        String amountStr = cfg.payRoundAmount
                ? String.valueOf((long) toPay)
                : String.valueOf(toPay);
        String cmd = stripSlash(cfg.payCommand)
                .replace("%target%", cfg.payTarget.trim())
                .replace("%amount%", amountStr);

        mc.player.connection.sendCommand(cmd);

        this.paidTotal += toPay;
        this.paymentCount++;
        this.state = State.COOLDOWN;
        this.nextActionMs = now + cfg.payCooldownSec * 1000L;

        String msg = "§aĐã chuyển §f" + amountStr + " §acho §f" + cfg.payTarget.trim()
                + " §7(số dư trước: " + formatMoney(balance) + ")";
        actionBar("§7[§6Auto Pay§7] " + msg);
        if (cfg.payChatLog)
        {
            mc.player.displayClientMessage(Component.literal("§7[§6Auto Pay§7] " + msg), false);
        }
    }

    /** Edge-detected poll of the configured toggle key — works with ANY key. */
    private void pollHotkey(Minecraft mc, AutoSellVdmConfig cfg)
    {
        boolean down = ItemHotkeyUtil.isDown(cfg.payHotkey);
        if (down && this.hotkeyWasDown == false && mc.screen == null)
        {
            this.toggle();
        }
        this.hotkeyWasDown = down;
    }

    private boolean hotkeyWasDown = false;

    static String stripSlash(String cmd)
    {
        String c = cmd == null ? "" : cmd.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    /** Local replacement for malilib's InfoUtils.printActionbarMessage. */
    private static void actionBar(String text)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }

    public static String formatMoney(double value)
    {
        if (value >= 1_000_000_000_000.0) return String.format("%.2fT", value / 1_000_000_000_000.0);
        if (value >= 1_000_000_000.0)     return String.format("%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000.0)         return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 1_000.0)             return String.format("%.1fK", value / 1_000.0);
        return String.format("%.0f", value);
    }
}
