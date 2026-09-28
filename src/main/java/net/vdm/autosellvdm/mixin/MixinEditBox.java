/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;


import net.vdm.autosellvdm.VdmTheme;

/**
 * Ô nhập trong menu của mod: thay cái khung texture của vanilla bằng khung tối
 * bo góc, giữ nguyên mọi thứ còn lại.
 *
 * <p>Chỉ chặn đúng lời gọi vẽ KHUNG ({@code blitSprite}), KHÔNG tắt cờ
 * {@code bordered}. Bản trước tắt cờ đó và dính lỗi ngay: vanilla vẽ chữ ở
 * chính giữa ô khi có khung, nhưng vẽ ở SÁT MÉP TRÊN khi không có — nên chữ
 * trong mọi ô nhập bị đội lên trên, lệch hẳn khỏi khung (thấy rõ trong ảnh của
 * user). Redirect thế này thì chữ vẫn nằm giữa và vẫn có lề 4px như thường.
 *
 * <p><b>{@code require = 0} là bắt buộc:</b> mod Litematica (fork) vá đúng lời
 * gọi này bằng một redirect y hệt, mà Mixin chỉ cho MỘT redirect trên một lời
 * gọi — cái thua bị bỏ qua và, với require mặc định là 1, sẽ ném
 * {@code InjectionError} làm CRASH ngay lúc nạp {@code EditBox}. Đặt 0 để cái
 * thua im lặng nhường, còn cái thắng thì tô cho màn hình của cả hai mod nhờ
 * {@link VdmTheme#isModScreen}.
 */
@Mixin(EditBox.class)
public class MixinEditBox
{
    @Redirect(method = "renderWidget", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite"
                            + "(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void autosellvdm$flatField(GuiGraphics g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height)
    {
        if (VdmTheme.isModScreen(Minecraft.getInstance().screen))
        {
            VdmTheme.field(g, x, y, width, height, ((EditBox) (Object) this).isFocused());
            return;
        }

        g.blitSprite(pipeline, sprite, x, y, width, height);
    }
}
