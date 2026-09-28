/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import net.vdm.autosellvdm.AutoPay;
import net.vdm.autosellvdm.AutoSell;
import net.vdm.autosellvdm.SellStats;

/**
 * Vòi nghe chat/actionbar ở TẦNG PACKET — thay cho Fabric
 * ClientReceiveMessageEvents, thứ mà Dawn Client nuốt mất (bản tự khám của user
 * đo được: 10 giây trên server đông mà {@code overlay=0}, tiền nhảy đầy màn
 * nhưng "dòng $ cuối: chưa thấy"). Packet hệ thống thì client nào cũng phải
 * nhận qua {@code handleSystemChat}, nên chốt ở đây là không mod client nào
 * giấu nổi: tiền bán/đào (SellStats), trả lời số dư (AutoPay) và thông báo
 * restart (AutoSell) đều đi qua vòi này.
 *
 * <p>{@code isSameThread()} là bắt buộc: vanilla gọi {@code handleSystemChat}
 * hai lần — lần đầu trên luồng mạng (rồi tự ném sang luồng chính), lần hai trên
 * luồng chính. Không chặn thì mỗi dòng tiền bị đếm ĐÔI.
 */
@Mixin(ClientPacketListener.class)
public class MixinSystemChatTap
{
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void autosellvdm$tap(ClientboundSystemChatPacket packet, CallbackInfo ci)
    {
        if (Minecraft.getInstance().isSameThread() == false)
        {
            return; // lượt gọi trên luồng mạng — lượt luồng chính mới là thật
        }

        Component message = packet.content();

        SellStats.parseMessage(message, packet.overlay());
        AutoPay.onChatMessage(message);
        AutoSell.onServerText(message.getString());
    }
}
