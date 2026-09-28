/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;


import net.vdm.autosellvdm.VdmTheme;

/**
 * Đổi font chữ sang Roboto trong lúc menu của mod đang mở.
 *
 * <p>{@code Font.getGlyphSource} là cửa duy nhất mọi chữ đi qua để lấy bộ glyph:
 * đổi tham số ở đây là ĐỔI HẾT — nhãn nút, ô nhập, tiêu đề, tên item trong danh
 * sách — mà không phải sờ vào từng lời gọi vẽ chữ (và không có kiểu chữ nào bị
 * sót lại font pixel, trông chắp vá).
 *
 * <p>Chỉ đụng khi style đang dùng font MẶC ĐỊNH và màn hình đang mở là của mod:
 * chat, HUD, GUI vanilla hay mod khác giữ nguyên font gốc.
 */
@Mixin(Font.class)
public class MixinFont
{
    @ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
    private FontDescription autosellvdm$modFont(FontDescription description)
    {
        if (description instanceof FontDescription.Resource resource
                && resource.id().equals(Minecraft.DEFAULT_FONT)
                && VdmTheme.isModScreen(Minecraft.getInstance().screen))
        {
            return VdmTheme.FONT;
        }

        return description;
    }
}
