/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

/** Exposes the tab-list header/footer so Auto Pay can read a balance shown there. */
@Mixin(PlayerTabOverlay.class)
public interface IMixinPlayerTabOverlay
{
    @Nullable
    @Accessor("header")
    Component autosellvdm_getHeader();

    @Nullable
    @Accessor("footer")
    Component autosellvdm_getFooter();
}
