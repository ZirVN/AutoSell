/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.

 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import net.vdm.autosellvdm.StaffGuard;

/**
 * No-Render vật phẩm rơi: chặn ngay ở {@code shouldRender} — entity vẫn tồn tại (vẫn nhặt được),
 * chỉ không vẽ nữa, nên farm xả cả thảm đồ cũng không tụt FPS.
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcherNoRender
{
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void autosellvdm_noRenderItems(E entity, Frustum frustum,
            double x, double y, double z, CallbackInfoReturnable<Boolean> cir)
    {
        if (entity instanceof ItemEntity item && StaffGuard.shouldHideItem(item))
        {
            cir.setReturnValue(false);
        }
    }
}


