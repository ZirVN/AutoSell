/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;

import net.vdm.autosellvdm.VdmStyledScreen;
import net.vdm.autosellvdm.VdmTheme;

/**
 * Vẽ nút phẳng kiểu Dawn thay cho texture nút của vanilla — CHỈ khi màn hình
 * đang mở là menu của mod ({@link VdmStyledScreen}).
 *
 * <p>Phải chặn ở đây vì {@code AbstractButton.renderWidget} là {@code final}:
 * không kế thừa để vẽ đè được, mà viết lại từng nút thành widget riêng thì phải
 * sửa hàng trăm dòng dựng layout. Mixin ở HEAD + cancel là cách rẻ nhất, và cái
 * cổng {@code instanceof VdmStyledScreen} giữ cho vanilla không bị đụng.
 *
 * <p>Cố tình KHÔNG {@code @Shadow} field kế thừa nào (x/y/width/height/active) —
 * shadow field của lớp cha là công thức gây crash lúc nạp mixin; ở đây chỉ dùng
 * getter public và field {@code active} qua tham chiếu thật.
 */
@Mixin(AbstractButton.class)
public class MixinAbstractButton
{
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void autosellvdm$flatStyle(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        Minecraft mc = Minecraft.getInstance();

        // Nhận cả màn hình của mod anh em: nếu cả hai mod cùng cài, cái nào chạy
        // trước cũng vẽ đúng kiểu rồi cancel, cái sau không chạy nữa (không vẽ đè).
        if (!VdmTheme.isModScreen(mc.screen))
        {
            return;
        }

        AbstractButton self = (AbstractButton) (Object) this;
        VdmTheme.button(g, mc.font, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.getMessage(), self.isHovered(), self.active);
        ci.cancel();
    }
}
