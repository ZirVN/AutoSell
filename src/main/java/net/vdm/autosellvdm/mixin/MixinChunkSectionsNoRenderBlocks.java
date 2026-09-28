/*
 * © 2026 vuducmanh09. Standalone mod.
 */
package net.vdm.autosellvdm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

import net.vdm.autosellvdm.AutoSellVdmConfig;

/** "Tắt hiển thị block" kiểu Meteor Xray opacity 0 — không vẽ terrain; block vẫn tồn tại + va chạm, entity vẫn vẽ. */
@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsNoRenderBlocks
{
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void autosellvdm_noRenderBlocks(CallbackInfo ci)
    {
        if (AutoSellVdmConfig.get().noRenderEnabled)
        {
            ci.cancel();
        }
    }
}
