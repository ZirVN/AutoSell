/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

import net.vdm.autosellvdm.VdmTheme;

/**
 * Nghiêng toàn bộ chữ trong menu của mod (kiểu 𝘴𝘰𝘮𝘦 𝘱𝘳𝘦𝘷𝘪𝘦𝘸 𝘵𝘦𝘹𝘵 user gửi).
 *
 * <p>Không có font italic nào dùng được: bộ font sẵn trên máy chỉ có serif
 * italic và mono italic, còn Inter italic là .otf/CFF mà Minecraft từ chối. Nên
 * dùng chính cơ chế nghiêng của game: {@code Style.withItalic} làm renderer xô
 * nghiêng từng glyph — áp lên font Droid Sans hiện tại là ra đúng dáng sans
 * nghiêng, khỏi cần thêm file font nào.
 *
 * <p>Chốt ở {@code PreparedTextBuilder.accept} — nơi MỌI glyph đi qua kèm style
 * của nó. Nhờ vậy nghiêng cả những chỗ code của mod không cầm được: chữ trong ô
 * nhập, tên item trong danh sách, nhãn nút do vanilla vẽ. Đặt {@code targets}
 * bằng chuỗi vì lớp này là package-private, không tham chiếu bằng class literal
 * được.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class MixinFontItalic
{
    @ModifyVariable(method = "accept", at = @At("HEAD"), argsOnly = true, require = 0)
    private Style autosellvdm$italic(Style style)
    {
        if (style != null && style.isItalic() == false
                && VdmTheme.isModScreen(Minecraft.getInstance().screen))
        {
            return style.withItalic(true);
        }

        return style;
    }
}
