/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * Client entry point for AutoSellVDM. Wires the ported Auto Sell / Auto Pay / Auto Reconnect logic
 * to Fabric's client events, registers the GUI keybind and the {@code /autosellvdm} command.
 */
public class AutoSellVdmClient implements ClientModInitializer
{
    /** Phien ban + jar dang chay — in mot khoi TU-KHAM ~10s sau khi vao the gioi. */
    public static String VERSION = "dev";
    // (Khoi TU KHAM khi vao the gioi da GO theo lenh user 2026-08-20 — nhiem vu
    // chan doan da xong: jar chuan, BalanceReader doc duoc so du.)
    private static KeyMapping openGuiKey;

    /** Edge-detect state for the config-stored player-alert hotkey. */
    private static boolean alertKeyWasDown;
    /** Edge-detect state for the config-stored Auto Sell toggle hotkey. */
    private static boolean sellKeyWasDown;
    /** Edge-detect state for the config-stored Auto Sell stats toggle hotkey. */
    private static boolean statsKeyWasDown;

    @Override
    public void onInitializeClient()
    {
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("autosellvdm").ifPresent(
                c -> VERSION = "v" + c.getMetadata().getVersion().getFriendlyString());
        AutoSellVdmConfig.get(); // load config/autosellvdm.json

        // Keybind to open the GUI (default UNKNOWN — the user binds it in Controls).
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autosellvdm.openGui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC));

        // Per-tick drivers for every feature.
        ClientTickEvents.END_CLIENT_TICK.register(mc ->
        {
            while (openGuiKey.consumeClick())
            {
                if (mc.screen == null)
                {
                    mc.setScreen(new GuiAutoSellVdm(null));
                }
            }

            // Must run BEFORE the key gate. Once a reconnect has been scheduled we are sitting on the
            // title screen with no player, and the pending rejoin is the only thing that can get us back
            // in — if the gate could swallow it the player would be stranded at the main menu. tick()
            // only acts on an already-scheduled rejoin, and starting one still requires being in a
            // world, so nothing here bypasses the key check.
            AutoReconnect.getInstance().tick(mc);

            pollHotkeys(mc);
            // Staff List + Auto Sign + No-Render (đặt TRƯỚC AutoSell/AutoPay để cờ frozen kịp chặn).
            StaffGuard.tick(mc);
            // No SellStats.onTick() — the Discord report schedule lives in AutoSell.tickWebhook(),
            // which runs even while selling is off so reports don't stop with it.
            SellStats.tickBalance(mc); // doanh thu theo chenh lech so du
            AutoSell.getInstance().onClientTick(mc);
            AutoPay.getInstance().onClientTick(mc);
            PlayerAlert.getInstance().onClientTick(mc);
        });

        // On-screen stats HUD.
        HudRenderCallback.EVENT.register(new AutoSellHud());
        HudRenderCallback.EVENT.register((g, delta) -> StaffGuard.renderHud(g));

        // Chat/overlay GIO NGHE O TANG PACKET (MixinSystemChatTap): Dawn Client
        // nuot Fabric ClientReceiveMessageEvents (tu kham do duoc overlay=0),
        // nen tien/so du/restart deu doc thang tu handleSystemChat — khong dang
        // ky event o day nua keo client thuong bi dem doi.

        // World (re)join / disconnect lifecycle.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
        {
            AutoSell.onJoin();
            AutoPay.onJoin();
            // Must run after AutoSell.onJoin(): it consumes the "was this an auto reconnect" flag and, if
            // so, restarts the sell cycle — which would then be wiped by onJoin's reset.
            AutoReconnect.onJoin();
            // Dong ho stats chay VO DIEU KIEN tu luc vao the gioi — truoc nup sau co
            // autoSellStats, co tat (lo bam hotkey) la bao cao gui deu ma dong ho
            // dung im 00:00:00, so nao cung 0 (anh user chup).
            SellStats.start();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            if (AutoSellVdmConfig.get().autoSellEnabled)
            {
                AutoSell.pause();
            }
            AutoReconnect.onLeave();
            SellStats.stop();
        });

        // /autosellvdm → mở GUI.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("autosellvdm").executes(ctx ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new GuiAutoSellVdm(null)));
                    return 1;
                })));
    }

    /** Edge-detected poll of every raw-GLFW-code hotkey stored in the config (not a KeyMapping). */
    private static void pollHotkeys(Minecraft mc)
    {
        AutoSellVdmConfig cfg = AutoSellVdmConfig.get();

        boolean alertDown = ItemHotkeyUtil.isDown(cfg.playerAlertHotkey);
        if (alertDown && alertKeyWasDown == false && mc.screen == null)
        {
            PlayerAlert.getInstance().toggle();
        }
        alertKeyWasDown = alertDown;

        boolean sellDown = ItemHotkeyUtil.isDown(cfg.autoSellToggleHotkey);
        if (sellDown && sellKeyWasDown == false && mc.screen == null)
        {
            AutoSell.getInstance().toggle();
        }
        sellKeyWasDown = sellDown;

        boolean statsDown = ItemHotkeyUtil.isDown(cfg.autoSellStatsToggleHotkey);
        if (statsDown && statsKeyWasDown == false && mc.screen == null)
        {
            boolean on = cfg.autoSellStats == false;
            cfg.autoSellStats = on;
            cfg.save();
            if (mc.player != null)
            {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(on
                                ? "§7[§6AutoSell§7] §aThống kê: BẬT (đếm lại từ đầu)"
                                : "§7[§6AutoSell§7] §cThống kê: TẮT"), true);
            }
            if (on)
            {
                SellStats.reset();
            }
        }
        statsKeyWasDown = statsDown;
    }
}
