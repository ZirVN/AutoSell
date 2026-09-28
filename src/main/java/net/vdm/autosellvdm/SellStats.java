/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 * Ported from the Litematica fork's autosell.SellStats.
 */
package net.vdm.autosellvdm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Counters for {@link AutoSell}: revenue, number of sales and total items moved, plus the session
 * uptime the rate is derived from. Revenue comes either from parsing money amounts out of server
 * chat, or from the flat {@code autoSellValuePerSale} per moved stack.
 */
public class SellStats
{
    // "$1234", "+ $1.5K", "$3 M" … number with optional K/M/B suffix.
    private static final Pattern MONEY_PATTERN =
            Pattern.compile("(?i)(?:\\+\\s*)?\\$[ \\t]*([0-9,]+(?:\\.[0-9]+)?)\\s*([KkMmBb]?)");

    private static double totalRevenue = 0.0;
    private static int totalSalesCount = 0;
    private static long totalItemsSold = 0L;
    private static long startTimeMs = 0L;

    /** Wipe the counters and start a fresh session. */
    public static void reset()
    {
        totalRevenue = 0.0;
        balanceRise = 0.0;
        totalSalesCount = 0;
        totalItemsSold = 0L;
        startTimeMs = System.currentTimeMillis();
    }

    /** Alias kept for the client entry point's world-join hook. */
    public static void start()
    {
        reset();
        resetBalanceBaseline();
    }

    public static void stop()
    {
        startTimeMs = 0L;
    }

    public static void recordSale(double amount)
    {
        // KHONG con cong autoSellStats: bo dem chay vo dieu kien (dem khong ton gi),
        // co do gio chi dieu khien the HUD. Truoc day co tat (lo bam hotkey) la moi
        // so ve 0 + dong ho 00:00:00 trong khi bao cao van gui deu — dung canh user chup.
        ensureStarted();
        totalRevenue += amount;
        totalSalesCount++;
    }

    public static void recordItemsSold(int count)
    {
        ensureStarted();
        totalItemsSold += count;
    }

    // ---- Bo dem tu-kham (diag): dem su kien chat/overlay + dong tien cuoi ----
    private static long chatEvents = 0;
    private static long gameEvents = 0;
    private static String lastMoneySeen = "";

    public static long chatEvents() { return chatEvents; }
    public static long gameEvents() { return gameEvents; }
    public static String lastMoneySeen() { return lastMoneySeen; }
    public static boolean isBalanceTracking() { return balanceTracking; }
    public static double lastBalance() { return lastBalance; }

    /** Extract the first money amount from an incoming chat line and count it as revenue. */
    public static void parseMessage(Component message, boolean isOverlay)
    {
        if (isOverlay) { gameEvents++; } else { chatEvents++; }
        String raw = message == null ? null : message.getString();
        if (raw != null && raw.indexOf(36) >= 0) { lastMoneySeen = raw; } // 36 = kytu $
        parseChatMoney(raw);
    }

    public static void parseChatMoney(String text)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        // Chat parsing off → AutoSell credits a flat amount per sale instead. Reading chat as well
        // would double-count every sale.
        if (cfg.autoSellChatParsing == false
                || text == null || text.isEmpty())
        {
            return;
        }

        String lower = text.toLowerCase();

        // Never count money that isn't our own sale: other players' balances, payments we send,
        // baltop, auctions, etc. (seen on the server: "X has $..", "You paid X $..").
        if (lower.contains("has $") || lower.contains("paid") || lower.contains("balance")
                || lower.contains("baltop") || lower.contains("bought your") || lower.contains("/pay"))
        {
            return;
        }

        // Skip normal player chat ("<name> ...") and unrelated system lines with a colon,
        // unless they mention selling — cuts down on false positives.
        if (text.contains("<") && text.contains(">"))
        {
            return;
        }
        if (text.contains(":") && !lower.contains("bán") && !lower.contains("sell"))
        {
            return;
        }

        Matcher matcher = MONEY_PATTERN.matcher(text);

        if (matcher.find())
        {
            try
            {
                double amount = Double.parseDouble(matcher.group(1).replace(",", ""));

                switch (matcher.group(2).toUpperCase())
                {
                    case "K" -> amount *= 1_000.0;
                    case "M" -> amount *= 1_000_000.0;
                    case "B" -> amount *= 1_000_000_000.0;
                    default -> { }
                }

                recordSale(amount);
            }
            catch (Exception ignored) { }
        }
    }

    // ---- Doanh thu theo CHENH LECH SO DU (nguon chinh) ----------------------
    // Thoi doan format chat: server hien tien moi kieu (action bar, feed, co
    // chu "balance" bi bo loc cu nuot...). So du thi luon nhin thay tren
    // scoreboard/tab — tang bao nhieu cong bay nhieu, ban hay dao ra tien deu
    // dinh. Chat-parse tu dong NHUONG khi kenh nay hoat dong (khoi dem doi).

    // Tong muc DANG cua so du trong phien — SAN cua doanh thu. So du tren HUD
    // thuong bi lam tron (kieu "186M"): ban le 6.3K khong doi hien thi, delta = 0
    // — nen khong the de kenh nay DE len chat-parse. Hai kenh chay song song,
    // doanh thu bao cao = max(chat, san so du): khong dem doi, khong ai bit ai.
    private static double balanceRise = 0.0;
    private static double lastBalance = -1.0;
    private static boolean balanceTracking = false;
    private static int balanceTickTimer = 0;

    /** Goi moi tick tu client; tu gioi han 1 lan/giay. */
    public static void tickBalance(Minecraft mc)
    {
        if (mc == null || mc.player == null || ++balanceTickTimer < 20)
        {
            return;
        }
        balanceTickTimer = 0;

        double balance = BalanceReader.read(mc);

        if (balance < 0)
        {
            return; // khong doc duoc (chua vao server, sidebar tat) — giu moc cu
        }
        if (lastBalance >= 0 && balance > lastBalance)
        {
            ensureStarted();
            balanceRise += balance - lastBalance;
        }
        // Giam (mua do, /pay di) chi doi moc, khong tru doanh thu.
        lastBalance = balance;
        balanceTracking = true;
    }

    public static void resetBalanceBaseline()
    {
        lastBalance = -1.0;
        balanceTickTimer = 0;
    }

    private static void ensureStarted()
    {
        if (startTimeMs == 0L)
        {
            startTimeMs = System.currentTimeMillis();
        }
    }

    /** Start the session clock without recording a sale — used by the webhook test. */
    public static void ensureTracking()
    {
        ensureStarted();
    }

    public static boolean isTracking()
    {
        return startTimeMs != 0L;
    }

    public static long getElapsedMs()
    {
        return startTimeMs == 0L ? 0L : System.currentTimeMillis() - startTimeMs;
    }

    public static double getItemsPerHour()
    {
        long elapsed = getElapsedMs();
        return elapsed <= 0L ? 0.0 : totalItemsSold / (elapsed / 3_600_000.0);
    }

    public static double getEarnedPerHour()
    {
        long elapsed = getElapsedMs();
        return elapsed <= 0L ? 0.0 : getTotalEarned() / (elapsed / 3_600_000.0);
    }

    public static double getTotalEarned()
    {
        return Math.max(totalRevenue, balanceRise);
    }

    public static int getTotalSalesCount()
    {
        return totalSalesCount;
    }

    public static long getTotalItemsSold()
    {
        return totalItemsSold;
    }

    /** The player name and server address the webhook report is stamped with. */
    public static String playerName()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
        {
            return "Unknown";
        }
        // Ten PROFILE goc, khong phai display name: client custom (Dawn) de len
        // getName() lam ten ve chuoi rong — embed ra avatar Steve + o ten trong.
        String profileName = mc.player.getGameProfile().name();
        return profileName == null || profileName.isBlank()
                ? mc.player.getName().getString() : profileName;
    }

    public static String serverIp()
    {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        return sd != null && sd.ip != null ? sd.ip : "Singleplayer";
    }

    public static String formatDuration()
    {
        long secs = getElapsedMs() / 1000L;
        return String.format("%02d:%02d:%02d", secs / 3600L, secs % 3600L / 60L, secs % 60L);
    }

    public static String formatCurrency(double amount)
    {
        if (amount >= 1_000_000_000.0)
        {
            return String.format("$%.2fB", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000.0)
        {
            return String.format("$%.2fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000.0)
        {
            return String.format("$%.2fK", amount / 1_000.0);
        }
        return String.format("$%.0f", amount);
    }
}
